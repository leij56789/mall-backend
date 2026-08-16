package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.common.SeckillConstants;
import com.mall.config.MessageProperties;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.dto.response.SeckillResponse;
import com.mall.entity.*;
import com.mall.enums.*;
import com.mall.interceptor.JwtInterceptor;
import com.mall.mapper.BookMapper;
import com.mall.mapper.SeckillBookMapper;
import com.mall.mapper.SeckillRecordMapper;
import com.mall.mapper.UserMapper;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.SeckillMessage;
import com.mall.mq.producer.SeckillOrderMessageProducer;
import com.mall.mq.producer.SeckillProducer;
import com.mall.service.*;
import com.mall.utils.TransactionRollbackCallback;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.mall.common.SeckillConstants.*;

/**
* @author jiaolei
* @description 针对表【seckill_book】的数据库操作Service实现
* @createDate 2026-06-30 15:42:20
*/
@Slf4j
@Service
public class SeckillBookServiceImpl extends ServiceImpl<SeckillBookMapper, SeckillBook>
    implements SeckillBookService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    SeckillBookMapper seckillBookMapper;
    @Autowired
    SeckillRecordMapper seckillRecordMapper;
    @Autowired
    SeckillProducer seckillProducer;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SnowflakeIdGenerator snowflakeIdGenerator;
    @Autowired
    MessageProperties messageProperties;
    @Autowired
    OrdersService ordersService;
    @Autowired
    SeckillOrderMessageProducer seckillOrderMessageProducer;
    @Autowired
    RedisRollbackService redisRollbackService;
    @Autowired
    AlertService alertService;
    @Autowired
    RedissonClient redissonClient;

//    private static final DefaultRedisScript<Long> SECKILL_SCRITP = new DefaultRedisScript<>();
//    static{
//        SECKILL_SCRITP.setLocation(new ClassPathResource("lua/seckill.lua"));
//        SECKILL_SCRITP.setResultType(Long.class);
//    }
    private static final String SECKILL_SCRIPT = """
        if redis.call('SISMEMBER', KEYS[2],ARGV[3])==1 then
            return -1
        end
        local stock = redis.call('GET', KEYS[1])
        if not stock or tonumber(stock) < tonumber(ARGV[1]) then
            return 0
        end
        redis.call('DECRBY', KEYS[1], ARGV[1])
        redis.call('SADD', KEYS[2], ARGV[3])
        redis.call('EXPIRE', KEYS[2], 3600)
        return 1
        """;

    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private UserMapper userMapper;

    @Log("秒杀入口")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public SeckillResponse seckill(Long bookId, Integer quantity) {
        MDC.put("bookId",String.valueOf(bookId));

        if(quantity==null||quantity!= DEFAULT_USER_LIMIT){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        String currentUsername = JwtInterceptor.getCurrentUser();
        if(currentUsername==null){
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, currentUsername));
        if(user==null){
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        MDC.put("userId",String.valueOf(user.getId()));
        if(bookId==null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        String stockKey= RedisKeys.SECKILL_STOCK +bookId;
        String userKey= RedisKeys.SECKILL_USER+bookId+":"+user.getId();
        String usersKey= RedisKeys.SECKILL_USERS+bookId+":";
        String seckillBookKey=RedisKeys.SECKILL_BOOK+bookId;
        //加载缓存
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        String seckillBookJson= stringRedisTemplate.opsForValue().get(seckillBookKey);
        SeckillBook seckillBook = null;
        if(stockStr==null||seckillBookJson==null){
            String lockKey = RedisKeys.SECKILL_LOCK + bookId;
            RLock lock = redissonClient.getLock(lockKey);
            Boolean locked = null;
            try {
                locked = lock.tryLock(0,-1, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("redission分布式锁异常",e);
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            log.info("🔒 获取锁：key={}, locked={}, thread={}", lockKey, locked, Thread.currentThread().getName());
            if(Boolean.TRUE.equals(locked)){
                try {
                    seckillBook = seckillBookMapper.selectOne(new LambdaQueryWrapper<SeckillBook>()
                            .eq(SeckillBook::getBookId, bookId));
                    if(seckillBook==null){
                        throw new BusinessException(ResultCode.SECKILL_NOT_EXIST);
                    }
                    seckillBookJson=objectMapper.writeValueAsString(seckillBook);
                    stringRedisTemplate.opsForValue().set(seckillBookKey,seckillBookJson,RedisKeys.TTL_SECKILL_BOOK);
                    stockStr = String.valueOf(seckillBook.getStock());
                    log.info("唯一一次从数据库导入缓存：bookId={},userId={},stock={}",bookId,user.getId(),stockStr);
                    stringRedisTemplate.opsForValue().set(stockKey, stockStr,RedisKeys.TTL_SECKILL_STOCK);
                } catch (Exception e) {
                    log.error("缓存秒杀商品失败",e);
                    throw new RuntimeException(e);
                } finally {
                    log.info("🔓 释放锁：key={}, thread={}", lockKey, Thread.currentThread().getName());
                    if(lock.isHeldByCurrentThread()){
                        lock.unlock();
                    }
                }
            }else{
                // 等待其他线程加载完成（重试机制）
                log.info("⏳ 获取锁失败，等待缓存加载：key={}, thread={}", lockKey, Thread.currentThread().getName());
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(100);
                        stockStr = stringRedisTemplate.opsForValue().get(stockKey);
                        seckillBookJson= stringRedisTemplate.opsForValue().get(seckillBookKey);
                        if(stockStr!=null&&seckillBookJson!=null){
                            log.info("秒杀缓存已加载");
                            break;
                        }
                    } catch (InterruptedException e) {
                        log.error("秒杀缓存加载失败",e);
                        throw new BusinessException(ResultCode.SYSTEM_ERROR);
                    }
                }
            }
        }
        if(stockStr==null||seckillBookJson==null){
            log.error("秒杀缓存加载失败");
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        try {
            seckillBook = objectMapper.readValue(seckillBookJson, SeckillBook.class);
        } catch (JsonProcessingException e) {
            log.error("json解析失败，seckillBookJson={}",seckillBookJson);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        if(seckillBook==null){
            log.error("秒杀缓存数据为空：stock={},seckillBook={}",stockStr,seckillBook);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        //活动时间判断
        if(seckillBook.getStartTime().isAfter(LocalDateTime.now())){
            throw new BusinessException(ResultCode.SECKILL_NOT_START);
        }
        if(seckillBook.getEndTime().isBefore(LocalDateTime.now())){
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }
        //预扣减库存
        Long result= null;
//        try {
//            result = stringRedisTemplate.execute(
//                    new DefaultRedisScript<>(SECKILL_SCRIPT,Long.class)
//                    ,Arrays.asList(stockKey,userKey)
//                    ,String.valueOf(quantity)
//                    ,String.valueOf(SeckillConstants.DEFAULT_USER_LIMIT)
//            );
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        try {
            result = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(SECKILL_SCRIPT,Long.class)
                    ,Arrays.asList(stockKey,usersKey)
                    ,String.valueOf(quantity)
                    ,String.valueOf(SeckillConstants.DEFAULT_USER_LIMIT)
                    ,String.valueOf(user.getId())
            );
        } catch (Exception e) {
            log.error("秒杀lua脚本执行失败，bookId={}，userId{},quantity{}",bookId,user.getId(),quantity);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        if(result==null){
            log.error("秒杀lua脚本回滚，bookId={}",bookId);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        switch (result.intValue()){
            case -1:
                throw new BusinessException(ResultCode.REPEAT_ORDER);
            case 0:
                throw new BusinessException(ResultCode.STOCK_EMPTY);
            case 1:
                break;
            default:
                log.error("秒杀lua脚本结果异常，result={}",result);
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
//        log.info("测试lua脚本原子性，userId={},redisStock={}",user.getId(),stringRedisTemplate.opsForValue().get(stockKey));
        //redis回滚与事务回滚同步
        try {
            TransactionRollbackCallback.registerRollbackAction(()->{
                log.info("事务回滚，触发自动回滚Redis,bookId={},userId={}",bookId,user.getId());
                redisRollbackService.rollbackRedisSeckillOrThrow(bookId,user.getId(),null);
            });
        } catch (Exception e) {
            // ✅ 只有日志 + 告警，没有补偿表
            log.error("【严重告警】Redis回调回滚注册失败，请人工处理：bookId={}, userId={}", bookId, user.getId(), e);
            alertService.sendAlert("Redis回调回滚注册失败", "bookId=" + bookId + ", userId=" + user.getId());
        }

        //秒杀记录
        SeckillRecord seckillRecord = SeckillRecord.builder().userId(user.getId()).bookId(bookId).status(SeckillRecordStatus.PENDING.getCode()).build();
        int inserted = 0;
        try {
            inserted = seckillRecordMapper.insert(seckillRecord);
        } catch (Exception e) {
            log.error("插入秒杀记录失败，userId={},bookId={}",user.getId(),bookId,e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        if(inserted!=1){
            throw new BusinessException(ResultCode.SECKILL_RECORD_INSERT_FAIL);
        }
        Book book = bookMapper.selectById(bookId);
        if(book==null){
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
        }

        //核心功能lua扣库存，DB写操作完成
        SeckillMessage seckillMessage = SeckillMessage.builder()
                .userId(user.getId())
                .bookId(bookId)
                .quantity(quantity).seckillPrice(seckillBook.getSeckillPrice())
                .recordId(seckillRecord.getId())
                .messageId(UUID.randomUUID().toString().replace("-",""))
                .timestamp(System.currentTimeMillis()).build();

        seckillProducer.sendSeckillMessage(seckillMessage);

        // 预扣成功后，记录排队顺序
        String queueKey = RedisKeys.SECKILL_QUEUE + bookId;
// 使用时间戳作为分数，保证先进先出
        Boolean added= stringRedisTemplate.opsForZSet().add(queueKey, String.valueOf(user.getId()), System.currentTimeMillis());
        if(!added){
            log.error("记录参与秒杀用户排队失败，bookId={},userId={}",bookId,user.getId());
        }
        stringRedisTemplate.expire(queueKey,RedisKeys.TTL_SECKILL_QUEUE);

// 获取当前用户的排名（从0开始，所以+1）
        Long rank = stringRedisTemplate.opsForZSet().rank(queueKey, user.getId().toString());
        int position = rank != null ? rank.intValue() + 1 : 0;  // 队列中第几位
        int estimatedWaitSeconds = (int) (ESTIMATEDMS *(position-1)/ 1000.0);

        SeckillResponse seckillResponse = SeckillResponse.builder().status(SeckillStatus.PENDING.getCode()).bookId(bookId)
                .bookName(book.getName()).bookCover(book.getCoverImage()).seckillPrice(seckillBook.getSeckillPrice()).quantity(quantity)
                .statusCode(SeckillRecordStatus.PENDING.getCode()).statusDesc(SeckillRecordStatus.PENDING.getDesc())
                .queuePosition(position).estimatedWaitSeconds(estimatedWaitSeconds)
                .message("正在处理，预计还需"+estimatedWaitSeconds+"秒").build();
        return seckillResponse;
    }

    @Log("处理秒杀订单")
    @Override
    @Transactional
    public void processSeckillOrder(SeckillMessage msg) {
        if(msg==null){
            log.error("seckillMessage为空，seckillMessage={}",msg);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        User currentUser = userMapper.selectById(msg.getUserId());
        if(currentUser==null){
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        Long bookId = msg.getBookId();
        String address = currentUser.getAddress();
        Integer quantity = msg.getQuantity();
        if(bookId==null||address==null||quantity==null){
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        SeckillBook seckillBook = seckillBookMapper.selectOne(new LambdaQueryWrapper<SeckillBook>()
                .eq(SeckillBook::getBookId, bookId));
        if(seckillBook==null){
            throw new BusinessException(ResultCode.SECKILL_NOT_EXIST);
        }
        //扣减库存
        if(seckillBook.getStock()<quantity){
            throw new BusinessException(ResultCode.STOCK_EMPTY);
        }
        seckillBook.setStock(seckillBook.getStock()-quantity);
        int rows = seckillBookMapper.updateById(seckillBook);
        if(rows==0){
            log.warn("乐观锁冲突：seckillBookId={},version={}",seckillBook.getId(),seckillBook.getVersion());
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT);
        }

        Orders orders = new Orders();
        orders.setAddress(address);
        orders.setQuantity(quantity);
        orders.setBookId(bookId);
        orders.setStatus(OrderStatus.PENDING.getValue());
        BigDecimal totalAmount = seckillBook.getSeckillPrice().multiply(BigDecimal.valueOf(quantity));
        orders.setTotalAmount(totalAmount);
        orders.setUserId(currentUser.getId());
        orders.setExpireTime(LocalDateTime.now().plusSeconds(messageProperties.getSeckillDelayTime()/1000));
        orders.setOrderNo(String.valueOf(snowflakeIdGenerator.nextId()));
        orders.setOrderType(OrderType.SECKILL.getCode());
        if(!ordersService.save(orders)){
            throw new BusinessException(ResultCode.ORDER_CREATE_FAIL);
        }
        Orders ordersRes = ordersService.getById(orders.getId());
        if(ordersRes==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        MDC.put("orderId",String.valueOf(orders.getId()));
        //消息队列处理订单超时
        seckillOrderMessageProducer.sendOrderTimeoutMessage(ordersRes
                ,RabbitMQConfig.SECKILL_DELAY_EXCHANGE
                ,RabbitMQConfig.SECKILL_DELAY_ROUTING_KEY);
        int updated = seckillRecordMapper.update(new LambdaUpdateWrapper<SeckillRecord>()
                .eq(SeckillRecord::getId,msg.getRecordId())
                .eq(SeckillRecord::getStatus, SeckillRecordStatus.PENDING.getCode())
                .set(SeckillRecord::getStatus, SeckillRecordStatus.SUCCESS.getCode())
                .set(SeckillRecord::getOrderId,ordersRes.getId()));
        if(updated!=1){
            throw new BusinessException(ResultCode.SECKILL_RECORD_UPDATE_FAIL);
        }
        //通知用户抢购成功


//        String stockKey=SECKILL_STOCK_KEY+bookId;
//        String seckillBookKey=SECKILL_SECKILLBOOK_KEY+bookId;
//        redisTemplate.delete(stockKey);
//        redisTemplate.delete(seckillBookKey);
    }
}




