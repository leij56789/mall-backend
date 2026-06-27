package com.mall.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 告警服务
 * 预留接口，后续可接入钉钉、飞书、邮件等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertService {

    /**
     * 发送告警
     */
    public void sendAlert(String title, String content) {
        // 1. 打印日志（至少保证有记录）
        log.error("【告警】{}：{}", title, content);
        
        // 2. TODO: 接入钉钉机器人
        // dingTalkClient.sendTextMessage(title + "\\n" + content);
        
        // 3. TODO: 接入飞书机器人
        // feiShuClient.sendTextMessage(title + "\\n" + content);
        
        // 4. TODO: 发送邮件
        // mailService.sendAlertEmail(title, content);
        
        // 5. TODO: 接入公司监控平台（如Prometheus + AlertManager）
        // meterRegistry.counter("message.send.failed", "orderId", orderId).increment();
    }
}