package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创建支付出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayResult {

    /** 支付单号 */
    private String paymentId;

    /** 状态（INIT / WAITING / PENDING_CONFIRM） */
    private String status;

    /** 支付链接（HTML 表单 / 跳转 URL / 二维码链接） */
    private String payUrl;

    /** 第三方预支付ID（微信需要，支付宝为空） */
    private String prepayId;

    /** 支付单过期时间 */
    private LocalDateTime expireAt;

    /** 扩展信息（如二维码、交易号等） */
    private Map<String, Object> extInfo;
}