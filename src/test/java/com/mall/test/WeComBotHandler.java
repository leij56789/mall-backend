package com.mall.test;

import io.github.cloudsen.ai.wecom.WeComAiBotClient;
import io.github.cloudsen.ai.wecom.WeComAiBotClientOptions;
import io.github.cloudsen.ai.wecom.model.*;
import io.github.cloudsen.ai.common.JsonSupport;
import org.junit.jupiter.api.Test;

public class WeComBotHandler {
    @Test
    void main() {
        // 配置
        String botId = "aibdjc5jFVoLZsk71eNlDzOGJsqaFXkZ5pJ";  // 直接填，不用 getenv
        String botSecret = "TYNrZd6fEBqUFPEQVkWE4IPwZFP9qgASJyttkjaMZB6";

        WeComAiBotClientOptions options = WeComAiBotClientOptions.builder(botId,botSecret).build();
        
        WeComAiBotClient client = new WeComAiBotClient(options);
        
        // ==== 注册所有监听器 ====
        
        // 1. 认证成功
        client.onAuthenticated(() -> System.out.println("✅ 认证成功"));
        
        // 2. 文本消息
        client.onTextMessage(frame -> {
            BaseMessage msg = frame.getBody();
            String content = msg.getText().getContent();
            String userId = msg.getFrom().getUserid();
            System.out.println("📩 " + userId + ": " + content);
            client.replySimpleText(frame, "收到：" + content);
        });
        
        // 3. 图片消息
        client.onImageMessage(frame -> {
            BaseMessage msg = frame.getBody();
            String url = msg.getImage().getUrl();
            System.out.println("📷 图片URL: " + url);
            client.replySimpleText(frame, "收到图片！");
        });
        
        // 4. 语音消息（已转文字）
        client.onVoiceMessage(frame -> {
            BaseMessage msg = frame.getBody();
            String recognized = msg.getVoice().getContent();
            System.out.println("🎤 语音转文字: " + recognized);
            client.replySimpleText(frame, "你说了：" + recognized);
        });
        
        // 5. 图文混排
        client.onMixedMessage(frame -> {
            BaseMessage msg = frame.getBody();
            for (MixedItem item : msg.getMixed().getMsgItems()) {
                if ("text".equals(item.getMsgtype())) {
                    System.out.println("📝 " + item.getText().getContent());
                } else if ("image".equals(item.getMsgtype())) {
                    System.out.println("📷 " + item.getImage().getUrl());
                }
            }
            client.replySimpleText(frame, "收到图文混排！");
        });
        
        // 6. 事件（进入会话）
        client.onEnterChat(frame -> {
            EventMessage eventMsg = frame.getBody();
            String userId = eventMsg.getFrom().getUserid();
            System.out.println("👤 用户 " + userId + " 进入会话");

            // 在 5 秒内回复欢迎语
            // SDK 提供了 replyWelcomeText 或 replyWelcomeTemplateCard 方法
            client.replyWelcomeText(frame, "欢迎使用本机器人！有什么可以帮你的吗？");
        });
        
        // 7. 通用消息（兜底）
        client.onMessage(frame -> {
            BaseMessage msg = frame.getBody();
            System.out.println("📨 收到消息类型: " + msg.getMsgType());
        });
        
        // 8. 错误处理
        client.onError(throwable -> {
            System.err.println("❌ 错误: " + throwable.getMessage());
            throwable.printStackTrace();
        });
        
        // ==== 启动连接 ====
        client.connect();
        System.out.println("🚀 机器人已启动");
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            client.disconnect();
        }
    }
}