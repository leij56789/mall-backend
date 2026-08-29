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
leij123321@outlook.com
cat ~/.ssh/id_rsa.pub
git remote set-url origin git@github.com:leij56789/mall-backend.git
git push
tree src/main/java/com/mall/
@PostConstruct
public void testMdc() {
MDC.put("testKey", "HELLO_WORLD");
log.info("MDC 测试日志：testKey 的值应该显示在方括号中");
MDC.clear();
}
#测试
秒杀业务测试树：我的测试理解是这样的，把整个业务流程看成一棵树，从树根到每个页的逻辑都跑通，数据上重点关注数据库库存和消息订单的状态变化，本业务的叶一共有超时取消成功最后log，消费者死信告警，生产者最大重试告警，补偿任务成功最后log和最大重试告警，其余有必要测但没发现的地方根据工作经验的积累添加
逻辑初步跑通容易忽视完善的地方：配置参数分散，redis的过期时间以及管理，业务的中间状态都要有相应接口访问，数据库中间件并发回滚的完善，日志流程很乱，应该做成有利于测试的log,测试标准化
redis回滚的位置：全异常（事务回滚时集中回滚），异常和异常重试的本阶段业务最终出口（每个出口处回滚，而库的回滚不跟随最终出口回滚）（不能事务集中处理，重试不能加事务），
技术核心：我觉得秒杀的技术核心在于多线程读写一致（横向一致），数据库，消息队列，缓存数据一致（纵向一致），其中纵向一致包含了reids回滚的三种不同情况（完全跟随@Trancational回滚，redis回滚和@Transactional回滚不完全一致，和@Transactional回滚相反），消息队列不能回滚（重试+状态码解决），数据库和消息队列和redis穿插行进
npm install -g localtunnel
lt --port 8080
your url is: https://wild-ants-55.loca.lt
备用地址
lt --port 8080 --host https://loca.lt
lt --port 8080 --host http://localtunnel.me
lt --port 8080 --subdomain mytest12345
监控8080
$env:DEBUG="*"; lt --port 8080
另一个获取公网地址的方案
.\\cloudflared.exe tunnel --url http://localhost:8080
npx cloudflared@latest tunnel --url http://localhost:8080 --log-level debug
npm install -g localtunnel
curl  https://merchants-expo-ruth-trip.trycloudflare.com/api/payment/callback/alipay -UseBasicParsing
lt -h "https://serverless.social" -p 8080
# 1. 快速测试服务是否可达
curl http://localhost:8080/actuator/health

# 2. 测试回调接口（不解析响应内容）
curl http://localhost:8080/api/payment/callback/alipay -UseBasicParsing

# 3. 模拟 POST 请求（如果需要）
curl -Method POST -Uri "http://localhost:8080/api/payment/callback/alipay" -UseBasicParsing
ngrok
ngrok config add-authtoken 3HiSQHLDPUsTM8GoFZEZU0CN3d5_XAx7KbcpqNKRTKTonMQJ
ngrok http --host-header="localhost:8080" 8080
ngrok start api --config ./ngrok.yml
Invoke-WebRequest -Uri "https://ounce-lustiness-synopses.ngrok-free.dev/api/payment/callback/alipay" -Headers @{"ngrok-skip-browser-warning" = "anyvalue"} -UseBasicParsing


Forwarding  https://随机字符.ngrok.io -> http://localhost:8080
ngrok config check
curl https://ounce-lustiness-synopses.ngrok-free.dev/api/payment/callback/alipay -UseBasicParsing
curl -H "ngrok-skip-browser-warning: anyvalue" https://ounce-lustiness-synopses.ngrok-free.dev/api/payment/callback/alipay -UseBasicParsing
curl -Uri "https://ounce-lustiness-synopses.ngrok-free.dev/api/payment/callback/alipay" -Headers @{"ngrok-skip-browser-warning" = "anyvalue"} -UseBasicParsing
Invoke-WebRequest -Uri "https://ounce-lustiness-synopses.ngrok-free.dev/api/payment/callback/alipay" -Method POST -Body "trade_status=TRADE_SUCCESS&out_trade_no=PAY_123456" -Headers @{"ngrok-skip-browser-warning" = "anyvalue"; "Content-Type" = "application/x-www-form-urlencoded"} -UseBasicParsing
保持运行：ngrok 的命令行窗口必须保持打开，关闭窗口隧道就会中断。
· 地址会变：每次启动，免费版的公网地址是随机生成的。重启后需要更新支付宝的配置。
· 访问警告页：访问 ngrok 地址时，可能会先看到一个中间警告页。解决方法是在请求头中添加 ngrok-skip-browser-warning: anyvalue。
· 连接失败：如果出现连接错误，可以检查 AuthToken 是否正确，或尝试更换网络环境。
· Web 界面：访问 http://127.0.0.1:4040 可以查看所有请求的日志，方便调试。
git 命令
# 1. 关闭 autocrlf
git config --global core.autocrlf false

# 2. 创建 .gitattributes
echo "* text=auto eol=lf" > .gitattributes

# 3. 重新规范化文件
git add --renormalize .
# 1. 查看当前改动
git status

# 2. 添加所有改动
git add .

# 3. 提交（使用方式一）
git commit -m "feat(payment): 完成支付宝 WAP 支付流程测试

- 新增 WAP 支付适配器，支持预下单、回调处理、状态同步
- WAP 支付端到端测试通过（沙箱环境 0.01 元成功）
- 二维码支付（F2F）暂未通过，疑似沙箱风控，需后续排查"

# 4. 推送到远程（可选）
git push

项目目录结构
# 进入项目根目录，然后执行
tree /F
验证沙箱是否正常
curl.exe -I "https://openapi-sandbox.dl.alipaydev.com/gateway.do"

