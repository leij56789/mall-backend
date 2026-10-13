package com.mall.test;

import io.github.cloudsen.ai.common.JsonSupport;
import io.github.cloudsen.ai.wecom.WeComAiBotClient;
import io.github.cloudsen.ai.wecom.WeComAiBotClientOptions;
import io.github.cloudsen.ai.wecom.model.BaseMessage;
import io.github.cloudsen.ai.wecom.model.WsFrame;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeComBotIntegrationTest {

    @Test
    void testConnection() throws Exception {
        String botId = "aibdjc5jFVoLZsk71eNlDzOGJsqaFXkZ5pJ";  // 直接填，不用 getenv
        String botSecret = "TYNrZd6fEBqUFPEQVkWE4IPwZFP9qgASJyttkjaMZB6";

        WeComAiBotClientOptions options = WeComAiBotClientOptions.builder(botId,botSecret).build();

        // 3. 创建客户端实例
        WeComAiBotClient client = new WeComAiBotClient(options);

        // 4. 注册监听器（必须在 connect() 之前注册）

        // 4.1 认证成功监听
        client.onAuthenticated(() -> {
            System.out.println("✅ 认证成功，长连接已建立！");
        });

        // 4.2 文本消息监听
        client.onTextMessage((WsFrame<BaseMessage> frame) -> {
            BaseMessage msg = frame.getBody();
            try {
                // 打印完整消息 JSON（调试用）
                String json = JsonSupport.getObjectMapper()
                                         .writeValueAsString(msg);
                System.out.println("📩 收到消息 JSON: " + json);

                // 提取文本内容
                if (msg.getText() != null) {
                    String content = msg.getText().getContent();
                    System.out.println("📝 文本内容: " + content);

                    // 回复消息（必须在5秒内）
                    client.replySimpleText(frame, "收到你的消息：" + content);
                } else {
                    System.out.println("⚠️ 不是文本消息，类型: " + msg.getMsgType());
                    client.replySimpleText(frame, "收到啦！但我只处理文本消息哦。");
                }
            } catch (Exception e) {
                System.err.println("❌ 处理消息异常: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // 4.3 所有消息监听（兜底）
        client.onMessage((WsFrame<BaseMessage> frame) -> {
            BaseMessage msg = frame.getBody();
            System.out.println("📨 收到通用消息，类型: " + msg.getMsgType() + ", ID: " + msg.getMsgId());
        });

        // 4.4 断开连接监听
        client.onDisconnected(reason -> {
            System.out.println("🔌 连接已断开，原因: " + reason);
        });

        // 4.5 错误监听
        client.onError(throwable -> {
            System.err.println("❌ 发生错误: " + throwable.getMessage());
            throwable.printStackTrace();
        });

        // 5. 建立连接
        System.out.println("🚀 机器人启动中，正在连接...");
        client.connect();

        // 6. 阻塞主线程，保持程序运行
        System.out.println("✅ 机器人已启动，等待消息...");
        try {
            Thread.currentThread().join();  // 永久阻塞，等待消息
        } catch (InterruptedException e) {
            System.err.println("程序被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        // 7. 程序退出前清理（正常情况下不会执行到这里）
        System.out.println("🛑 正在关闭客户端...");
        client.disconnect();

    }
}