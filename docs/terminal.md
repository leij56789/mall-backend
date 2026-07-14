rabbitmqctl delete_queue delay.queue
rabbitmqctl delete_queue orderTimeout.queue
rabbitmqctl delete_queue order.timeout.dlq
# 停止 RabbitMQ
rabbitmqctl stop_app

# 重置所有数据（包括队列、交换机、绑定）
rabbitmqctl reset

# 重新启动
rabbitmqctl start_app
redis-cli FLUSHALL
# 连接 Redis
redis-cli

# 查找所有秒杀相关的 key
KEYS seckill:*

# 删除所有秒杀相关的 key
redis-cli KEYS "seckill:*" | xargs redis-cli DEL
redis-cli -a your_password KEYS "seckill:*" | xargs redis-cli -a your_password DEL
# 删除所有 seckill: 开头的 key
redis-cli --scan --pattern "seckill:*" | xargs redis-cli DEL

# 删除所有 message:retry: 开头的 key
redis-cli --scan --pattern "message:retry:*" | xargs redis-cli DEL

# 删除所有 compensate: 开头的 key
redis-cli --scan --pattern "compensate:*" | xargs redis-cli DEL
# 查询redis
# 连接本地 Redis（默认端口）
redis-cli

# 连接指定主机和端口
redis-cli -h 127.0.0.1 -p 6379

# 有密码的情况
redis-cli -a your_password
# 连接本地 Redis（默认端口）
redis-cli

# 连接本地 Redis（默认端口）
redis-cli

# 连接指定主机和端口
redis-cli -h 127.0.0.1 -p 6379

# 有密码的情况
redis-cli -a your_password
# 查看所有 key
redis-cli KEYS "*"

# 查看秒杀相关的 key
redis-cli KEYS "seckill:*"

# 查看消息重试相关的 key
redis-cli KEYS "message:retry:*"

# 查看补偿锁
redis-cli KEYS "compensate:*"
redis-cli KEYS "seckill:*"
redis-cli KEYS "seckill:*" | ForEach-Object {
$key = $_;
$type = redis-cli TYPE $key;
Write-Host "=== $key ($type) ===";
if ($type -eq "string") { redis-cli GET $key }
elseif ($type -eq "set") { redis-cli SMEMBERS $key }
elseif ($type -eq "zset") { redis-cli ZRANGE $key 0 -1 WITHSCORES }
else { redis-cli GET $key }
}
redis-cli MONITOR | grep "seckill:lock"
redis-cli KEYS "seckill:lock:*"
#测试
秒杀业务测试树：我的测试理解是这样的，把整个业务流程看成一棵树，从树根到每个页的逻辑都跑通，数据上重点关注数据库库存和消息订单的状态变化，本业务的叶一共有超时取消成功最后log，消费者死信告警，生产者最大重试告警，补偿任务成功最后log和最大重试告警，其余有必要测但没发现的地方根据工作经验的积累添加
逻辑初步跑通容易忽视完善的地方：配置参数分散，redis的过期时间以及管理，业务的中间状态都要有相应接口访问，数据库中间件并发回滚的完善，日志流程很乱，应该做成有利于测试的log,测试标准化
redis回滚的位置：全异常（事务回滚时集中回滚），异常和异常重试的本阶段业务最终出口（每个出口处回滚，而库的回滚不跟随最终出口回滚）（不能事务集中处理，重试不能加事务），