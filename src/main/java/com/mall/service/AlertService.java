package com.mall.service;

/**
 * 告警服务接口
 * 支持不同级别的告警，用于支付、订单、系统异常等场景
 */
public interface AlertService {

    /**
     * 普通告警（钉钉/企业微信消息）
     */
    void sendAlert(String title, String content);

    /**
     * 高优先级告警（钉钉@指定人 + 邮件）
     */
    void sendUrgentAlert(String title, String content);

    /**
     * 严重告警（钉钉@所有人 + 邮件 + 短信，仅用于资金/安全类问题）
     */
    void sendCriticalAlert(String title, String content, Throwable e);
}