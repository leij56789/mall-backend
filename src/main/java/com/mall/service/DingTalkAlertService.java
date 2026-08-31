package com.mall.service;

import com.mall.config.DingTalkProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉告警服务（最终版）
 * <p>
 * 基于 OkHttpClient 实现，完全避免 RestTemplate 的编码问题。
 * 配置从 application.yml 读取，支持异步发送。
 */
@Slf4j
@Service
public class DingTalkAlertService {

    private final DingTalkProperties properties;
    private final OkHttpClient httpClient;

    @Autowired
    public DingTalkAlertService(DingTalkProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("===== 钉钉告警服务初始化 =====");
        log.info("enabled: {}", properties.isEnabled());
        log.info("webhook-url: {}", properties.getWebhookUrl());
        log.info("secret: {}", properties.getSecret());
        log.info("==============================");

        if (properties.isEnabled()) {
            // 延迟3秒，等待应用完全启动
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    sendTextAlert("【启动自检】钉钉告警服务已就绪 ✅");
                } catch (Exception e) {
                    log.error("启动自检消息发送失败", e);
                }
            }).start();
        }
    }

    /**
     * 发送文本告警（异步）
     */
    @Async
    public void sendTextAlert(String content) {
        if (!properties.isEnabled()) {
            log.debug("钉钉告警未启用，跳过发送");
            return;
        }
        try {
            String result = doSend(content);
            log.info("钉钉告警发送成功: {}", result);
        } catch (Exception e) {
            log.error("钉钉告警发送失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行发送（核心方法）
     * <p>
     * 注意：钉钉签名原文格式为 timestamp + "\\n" + secret
     * 其中 "\\n" 是换行符，不是 "\\\\n" 字面量！
     */
    private String doSend(String content) throws Exception {
        String secret = properties.getSecret();
        String webhookUrl = properties.getWebhookUrl();

        // 1. 获取时间戳（毫秒）
        long timestamp = System.currentTimeMillis();

        // 2. 构造待签名字符串
        // ✅ 关键：这里是 "\n"（换行符），不是 "\\n"（两个字符）
        String stringToSign = timestamp + "\n" + secret;

        // 3. HmacSHA256 签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

        // 4. Base64 编码
        String sign = Base64.getEncoder().encodeToString(signBytes);

        // 5. URL 编码
        String encodedSign = URLEncoder.encode(sign, StandardCharsets.UTF_8.name());

        // 6. 拼接完整 URL
        String url = webhookUrl + "&timestamp=" + timestamp + "&sign=" + encodedSign;

        log.debug("钉钉请求 URL: {}", url);

        // 7. 构建 JSON 请求体
        String json = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(content) + "\"}}";

        // 8. 发送请求
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String result = response.body() != null ? response.body().string() : "";
            log.debug("钉钉响应: {}", result);
            return result;
        }
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}