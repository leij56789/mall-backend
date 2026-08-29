package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.RedisKeys;
import com.mall.common.trace.util.AuditHashChainUtil;
import com.mall.entity.PaymentAuditLog;
import com.mall.mapper.PaymentAuditLogMapper;
import com.mall.service.AuditLogBuilder;
import com.mall.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAuditServiceImpl implements PaymentAuditService {

    private final PaymentAuditLogMapper auditLogMapper;
    private final AuditHashChainUtil hashChainUtil;
    private final RedissonClient redissonClient;
    @Lazy
    @Autowired
    private PaymentAuditService self;

    /**
     * 同步记录审计日志（带事务，确保哈希链正确）
     * <p>
     * 注意：此方法必须由外部调用，不能直接内部调用（否则事务失效）
     * 应通过 self.auditLog() 调用
     */
    @Transactional
    @Override
    public void auditLog(PaymentAuditLog auditLog) {
        String paymentId = auditLog.getPaymentId();

        // ========== 调试日志：查询并打印当前 paymentId 的所有审计记录 ==========
        List<PaymentAuditLog> existingLogs = auditLogMapper.selectByPaymentIdOrderByTime(paymentId);
        if (!existingLogs.isEmpty()) {
            log.info("=== 审计哈希链调试 (paymentId={}) ===", paymentId);
            for (PaymentAuditLog auditLogX : existingLogs) {
                String prevHash = auditLogX.getPrevHash() != null
                        ? (auditLogX.getPrevHash().length() >= 8
                           ? auditLogX.getPrevHash().substring(0, 8) + "..."
                           : auditLogX.getPrevHash())
                        : "null";

                String selfHash = auditLogX.getSelfHash() != null
                        ? (auditLogX.getSelfHash().length() >= 8
                           ? auditLogX.getSelfHash().substring(0, 8) + "..."
                           : auditLogX.getSelfHash())
                        : "null";
                log.info("  id={}, operation={}, prevHash={}, selfHash={}, created={}",
                        auditLogX.getId(), auditLogX.getOperation(), prevHash, selfHash, auditLogX.getCreatedAt());
            }
            log.info("=== 当前即将插入 ===");
            log.info("  operation={}, prevHash={}, selfHash={}",
                    auditLog.getOperation(),
                    auditLog.getPrevHash() != null ? auditLog.getPrevHash().substring(0, 8) + "..." : "null",
                    auditLog.getSelfHash() != null ? auditLog.getSelfHash().substring(0, 8) + "..." : "null");
            log.info("========================================");
        }

        // 1. 查询最近一条记录（带行锁）
        PaymentAuditLog prevLog = auditLogMapper.selectLastByPaymentId(auditLog.getPaymentId());

        // 2. 设置 prev_hash
        if (prevLog != null) {
            auditLog.setPrevHash(prevLog.getSelfHash());
        } else {
            auditLog.setPrevHash("INIT");
        }

        // 3. 计算 self_hash
        AuditHashChainUtil.AuditHashContent content = hashChainUtil.buildHashContent(auditLog, auditLog.getPrevHash());
        auditLog.setSelfHash(hashChainUtil.calculateSelfHash(content));

        // 4. 插入数据库
        try {
            auditLogMapper.insert(auditLog);
            log.info("审计日志写入成功: paymentId={}, operation={}, selfHash={}",
                    auditLog.getPaymentId(), auditLog.getOperation(),
                    auditLog.getSelfHash().substring(0, 8) + "...");
        } catch (Exception e) {
            // 审计日志记录失败不应影响主业务
            log.error("记录审计日志失败: paymentId={}, operation={}",
                    auditLog.getPaymentId(), auditLog.getOperation(), e);
        }
    }

    /**
     * 异步记录审计日志（带分布式锁，保证哈希链不并发分叉）
     * <p>
     * 锁的持有时间覆盖整个事务，确保查询时能看到已提交的上一条记录
     */
    @Async("audit")
    @Override
    public void logAsync(PaymentAuditLog auditLog) {
        String lockKey = RedisKeys.auditLock(auditLog.getPaymentId());
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，等待 3 秒，持有 30 秒（足够事务提交）
            boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取审计锁超时: paymentId={}, operation={}",
                        auditLog.getPaymentId(), auditLog.getOperation());
                return;
            }

            log.info("获取审计锁成功: paymentId={}, operation={}, thread={}",
                    auditLog.getPaymentId(), auditLog.getOperation(), Thread.currentThread().getName());

            // 通过代理调用事务方法，确保事务生效
            self.auditLog(auditLog);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("审计日志写入被中断: paymentId={}", auditLog.getPaymentId(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放审计锁: paymentId={}, operation={}",
                        auditLog.getPaymentId(), auditLog.getOperation());
            }
        }
    }

    /**
     * 获取上一条审计日志（用于查询接口）
     */
    @Override
    public PaymentAuditLog getLastAuditLog(String paymentId) {
        return auditLogMapper.selectOne(
                new LambdaQueryWrapper<PaymentAuditLog>()
                        .eq(PaymentAuditLog::getPaymentId, paymentId)
                        .orderByDesc(PaymentAuditLog::getCreatedAt)
                        .last("LIMIT 1")
        );
    }


    @Override
    public AuditLogBuilder builder() {
        return new AuditLogBuilder(this);
    }
//    public boolean verifyChain(List<PaymentAuditLog> logs) {
//        // 从第一条开始，逐条验证
//        for (int i = 0; i < logs.size(); i++) {
//            PaymentAuditLog current = logs.get(i);
//
//            // 1. 验证当前记录的 self_hash 是否正确
//            String expectedSelfHash = hashChainUtil.calculateSelfHash(current);
//            if (!expectedSelfHash.equals(current.getSelfHash())) {
//                return false;  // 哈希不匹配，说明记录被篡改
//            }
//
//            // 2. 验证下一条记录的 prev_hash 是否等于当前记录的 self_hash
//            if (i < logs.size() - 1) {
//                PaymentAuditLog next = logs.get(i + 1);
//                if (!current.getSelfHash().equals(next.getPrevHash())) {
//                    return false;  // 链断裂，说明中间有记录被删除或修改
//                }
//            }
//        }
//        return true;
//    }
}