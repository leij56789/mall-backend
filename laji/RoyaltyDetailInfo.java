package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 退分账明细信息
 */
@Data
@Builder
public class RoyaltyDetailInfo {
    /**
     * 分账金额（必填），单位：元
     */
    private String amount;

    /**
     * 分账接收方账号（必填）
     */
    private String transIn;

    /**
     * 分账类型（必填）
     * transfer：转账
     */
    private String royaltyType;

    /**
     * 分账支出方账号（必填）
     */
    private String transOut;

    /**
     * 分账支出方账号类型（必填）
     * userId：支付宝用户ID
     * loginId：支付宝登录号
     */
    private String transOutType;

    /**
     * 分账场景（必填）
     */
    private String royaltyScene;

    /**
     * 分账接收方账号类型（必填）
     * userId：支付宝用户ID
     * loginId：支付宝登录号
     */
    private String transInType;

    /**
     * 分账接收方姓名（可选）
     */
    private String transInName;

    /**
     * 分账描述（可选）
     */
    private String desc;
}