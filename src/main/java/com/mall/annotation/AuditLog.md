@AuditLog(
targetTypes = {AuditTargetType.ORDER},
orderId = "orderId",        // ⚠️ 注意：paymentId 在方法执行后才生成，切面需要在方法执行后获取
operation = AuditOperation.CREATE_PAYMENT,
desc = "创建支付单"
)
@Log("初始化支付单initPaymentWithLock")
@Transactional(rollbackFor = Exception.class)
public InitResult initPaymentWithLock(Long orderId, Long userId, String paymentMethod) {
// ...
}
@AuditLog(
targetTypes = {AuditTargetType.PAYMENT_ORDER},
paymentId = "paymentOrder.paymentId",   // ⚠️ 从对象中提取
operation = AuditOperation.CREATE_PAYMENT,
desc = "生成支付凭证（WAITING）"
)
@Transactional(rollbackFor = Exception.class)
public void updatePaymentStatusToWaitingFromStatusOnTransactional(
PaymentOrder paymentOrder, String paymentStatus, String prepayId, Map<String,Object> extInfo) {
// ...
}
@AuditLog(
targetTypes = {AuditTargetType.PAYMENT_ORDER},
paymentId = "payment.paymentId",        // ⚠️ 从对象中提取
operation = AuditOperation.PAYMENT_FAILED,
desc = "支付失败"
)
@Transactional
public void updatePaymentStatusToFailedFromStatusOnTransactional(PaymentOrder payment, String status) {
// ...
}
@AuditLog(
targetTypes = {AuditTargetType.PAYMENT_ORDER},
paymentId = "paymentOrder.paymentId",   // ⚠️ 从对象中提取
operation = AuditOperation.REFUND_SUCCESS,
desc = "退款成功更新支付单"
)
@Transactional
public void updateRefundSuccess(PaymentOrder paymentOrder, BigDecimal refundAmount) {
// ...
}
@AuditLog(
targetTypes = {AuditTargetType.PAYMENT_ORDER},
paymentId = "paymentOrder.paymentId",   // ?? 从对象中提取
operation = AuditOperation.REFUND_SUCCESS,
desc = "退款成功更新支付单"
)
@Transactional
public void updateRefundSuccess(PaymentOrder paymentOrder, BigDecimal refundAmount) {
// ...
}
@AuditLog(
targetTypes = {AuditTargetType.PAYMENT_ORDER},
paymentId = "payment.paymentId",        // ⚠️ 从对象中提取
operation = AuditOperation.CLOSE_PAYMENT,
desc = "关闭支付单"
)
@Async
public void closePaymentOrder(PaymentOrder payment) {
// ...
}
// 支持 paymentOrder.paymentId 这种格式
if (paramName.contains(".")) {
String[] parts = paramName.split("\\\\.");
Object obj = getParamValue(parts[0]);
if (obj != null) {
return getFieldValue(obj, parts[1]);  // 使用反射提取字段
}
}
