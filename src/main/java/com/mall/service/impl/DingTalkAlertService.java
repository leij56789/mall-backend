package com.mall.service.impl;

import com.mall.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkAlertService implements AlertService {

    private final RestClient restClient;

    @Value("${alert.dingtalk.webhook:}")
    private String webhookUrl;

    @Value("${alert.dingtalk.at-mobiles:}")
    private String[] atMobiles;

//    public DingTalkAlertService(RestTemplate restTemplate) {
//        this.restTemplate = restTemplate;
//    }

    @Override
    public void sendAlert(String title, String content) {
        String fullContent = buildMessage(title, content, "INFO");
        // 发送钉钉消息
        sendDingTalk(fullContent, false);
        // 同时记录日志（确保不丢失）
        log.info("【告警】{}: {}", title, content);
    }

    @Override
    public void sendUrgentAlert(String title, String content) {
        String fullContent = buildMessage(title, content, "URGENT");
        sendDingTalk(fullContent, true);
        log.error("【紧急告警】{}: {}", title, content);
        // 可扩展：发送邮件
    }

    @Override
    public void sendCriticalAlert(String title, String content, Throwable e) {
        // 构建消息内容
        String alertContent = content;
        if (e != null) {
            alertContent = content + "\\n异常信息: " + e.getMessage();
        }

        String fullContent = buildMessage(title, alertContent, "CRITICAL");
        sendDingTalk(fullContent, true);

        // 日志记录
        if (e != null) {
            log.error("【严重告警】{}: {}", title, content, e);
        } else {
            log.error("【严重告警】{}: {}", title, content);
        }

        // 可扩展：短信通知
    }

    private String buildMessage(String title, String content, String level) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("【%s】%s\\n时间: %s\\n详情: %s", level, title, timestamp, content);
    }

    private void sendDingTalk(String message, boolean atAll) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("钉钉 Webhook 未配置，告警仅记录日志: {}", message);
            return;
        }
        try {
            // 简单文本消息，也可改为 Markdown
            String payload = String.format(
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"%s\"},\"at\":{\"isAtAll\":%s}}",
                message.replace("\"", "\\\"").replace("\\n", "\\\\n"),
                atAll
            );
//            restClient.post()
//            restTemplatete.postForEntity(webhookUrl, payload, String.class);
        } catch (Exception e) {
            log.error("钉钉告警发送失败: {}", message, e);
        }
    }
}