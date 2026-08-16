@Service
@RequiredArgsConstructor
public class AlipayCallbackProcessor {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RedissonClient redissonClient;
    private final AlertService alertService;

    // 移除 @Transactional（事务下沉到业务层）
    @Override
    public String process(Map<String, String> params) {
        String paymentId = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");

        // ===== 1. 参数非空校验 =====
        if (paymentId == null || tradeStatus == null) {
            log.error("回调参数缺失: paymentId={}, tradeStatus={}", paymentId, tradeStatus);
            return "fail";
        }

        // ===== 2. 签名验证（必须） =====
        if (!verifyAlipaySign(params)) {
            log.error("支付宝回调验签失败: paymentId={}", paymentId);
            // 验签失败不重试，直接返回失败
            return "fail";
        }

        // ===== 3. 状态校验（只处理 TRADE_SUCCESS） =====
        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            // 官方只触发 TRADE_SUCCESS，如果收到其他状态，记录异常并返回 success（避免支付宝重试）
            log.warn("收到非 TRADE_SUCCESS 回调，忽略: paymentId={}, tradeStatus={}", paymentId, tradeStatus);
            alertService.sendAlert("收到非预期回调状态", "paymentId=" + paymentId + ", status=" + tradeStatus);
            // 返回 success 告诉支付宝不要再重试
            return "success";
        }

        // ===== 4. 业务校验（如金额比对等） =====
        if (!validateBusinessParams(params)) {
            log.error("业务校验失败: paymentId={}", paymentId);
            // 业务校验失败通常是参数异常，不应重试，返回 fail 让支付宝重试？但重试也无法修复，所以返回 success 并告警
            alertService.sendAlert("回调业务校验失败", "paymentId=" + paymentId);
            return "success";
        }

        // ===== 5. 分布式锁（防止与系统补偿任务并发） =====
        String lockKey = RedisKeys.PAYMENT_CALLBACK_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 等待 3 秒，持有 10 秒
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.warn("获取回调锁失败，可能正在被补偿任务处理: paymentId={}", paymentId);
                // 获取锁失败，返回 success（支付宝不会重试），由补偿任务最终处理
                return "success";
            }

            // ===== 6. 双重检查：支付单状态 =====
            PaymentOrder payment = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, paymentId)
            );
            if (payment == null) {
                log.error("支付单不存在: paymentId={}", paymentId);
                return "fail";
            }

            // 如果已经是 SUCCESS，幂等返回成功
            if (PaymentStatus.SUCCESS.getCode().equals(payment.getStatus())) {
                log.info("支付单已成功，幂等处理: paymentId={}", paymentId);
                return "success";
            }

            // ===== 7. 更新状态为 SUCCESS（事务方法） =====
            String thirdPartyTradeNo = params.get("trade_no");
            paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                    paymentId,
                    PaymentStatus.WAITING.getCode(),   // 从 WAITING 升级
                    thirdPartyTradeNo
            );
            log.info("回调处理成功: paymentId={}, tradeNo={}", paymentId, thirdPartyTradeNo);

            return "success";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: paymentId={}", paymentId);
            return "fail";
        } catch (BusinessException e) {
            // 业务异常（如乐观锁冲突）
            log.error("回调处理业务异常: paymentId={}, error={}", paymentId, e.getMessage());
            // 如果是状态已变更，可能已成功，查一下
            PaymentOrder latest = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, paymentId)
            );
            if (latest != null && PaymentStatus.SUCCESS.getCode().equals(latest.getStatus())) {
                return "success";
            }
            alertService.sendAlert("回调处理失败", "paymentId=" + paymentId + ", error=" + e.getMessage());
            return "fail";
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // 验签方法（略）
    private boolean verifyAlipaySign(Map<String, String> params) { /* 实现 */ }

    // 业务校验（如金额比对）
    private boolean validateBusinessParams(Map<String, String> params) { /* 实现 */ }
}