package com.mall.test;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cloudsen.ai.common.JsonSupport;
import io.github.cloudsen.ai.wecom.WeComAiBotClient;
import io.github.cloudsen.ai.wecom.WeComAiBotClientOptions;
import io.github.cloudsen.ai.wecom.model.*;
import okhttp3.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateCardDemo {
    // 在类中维护一个 Map，存储 msgId -> responseUrl
    private static final Map<String, String> responseUrlCache = new ConcurrentHashMap<>();
    public static void main(String[] args) {
        String botId = "aibdjc5jFVoLZsk71eNlDzOGJsqaFXkZ5pJ";
        String botSecret = "TYNrZd6fEBqUFPEQVkWE4IPwZFP9qgASJyttkjaMZB6";

        WeComAiBotClientOptions options = WeComAiBotClientOptions.builder(botId, botSecret).build();
        WeComAiBotClient client = new WeComAiBotClient(options);

        // 监听文本消息
        client.onTextMessage(frame -> {
            BaseMessage msg = frame.getBody();
            String content = msg.getText().getContent();
            // 保存 response_url
            if (msg.getResponseUrl() != null) {
                responseUrlCache.put(msg.getMsgId(), msg.getResponseUrl());
                System.out.println("💾 保存 response_url: " + msg.getMsgId() + " -> " + msg.getResponseUrl());
            }

            if ("卡片".equals(content)) {
                String taskId = "task_" + System.currentTimeMillis();
                Map<String, Object> templateCard = buildButtonCard(taskId);
                client.replyTemplateCard(frame, templateCard, null);
                System.out.println("✅ 已发送按钮卡片, taskId: " + taskId);
            } else {
                client.replySimpleText(frame, "发送「卡片」试试看！");
            }
        });

        // 监听模板卡片事件
        client.onTemplateCardEvent(frame -> {
            EventMessage eventMsg = frame.getBody();
            handleCardEvent(client, frame, eventMsg);
        });

        client.onAuthenticated(() -> System.out.println("✅ 认证成功"));
        client.connect();
        System.out.println("🚀 机器人已启动，发送「卡片」测试");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            client.disconnect();
        }
    }

    /**
     * 构建一个带两个按钮的交互卡片
     */
    private static Map<String, Object> buildButtonCard(String taskId) {
        Map<String, Object> card = new LinkedHashMap<>();

        card.put("card_type", "button_interaction");

        // 来源
        Map<String, String> source = new LinkedHashMap<>();
        source.put("icon_url", "https://wework.qpic.cn/wwpic/xxx_icon");
        source.put("desc", "来自智能助手");
        card.put("source", source);

        // 主标题 - 显示 taskId
        Map<String, String> mainTitle = new LinkedHashMap<>();
        mainTitle.put("title", "📋 工单确认");
        mainTitle.put("desc", "taskId: " + taskId);  // 🔥 显示 taskId
        card.put("main_title", mainTitle);

        // 正文
        Map<String, String> emphasis = new LinkedHashMap<>();
        emphasis.put("title", "工单 #12345");
        emphasis.put("desc", "客户反馈系统登录异常");
        card.put("emphasis_content", emphasis);

        // 按钮
        List<Map<String, Object>> buttons = new ArrayList<>();

        Map<String, Object> btn1 = new LinkedHashMap<>();
        btn1.put("text", "✅ 确认处理");
        btn1.put("key", "confirm");
        btn1.put("style", 1);
        buttons.add(btn1);

        Map<String, Object> btn2 = new LinkedHashMap<>();
        btn2.put("text", "❌ 驳回");
        btn2.put("key", "reject");
        btn2.put("style", 2);
        buttons.add(btn2);

        card.put("button_list", buttons);

        card.put("task_id", taskId);

        return card;
    }

    /**
     * 处理卡片按钮点击事件
     */
    private static void handleCardEvent(WeComAiBotClient client,
                                        WsFrame<EventMessage> frame,
                                        EventMessage eventMsg) {
        try {
            EventPayload event = eventMsg.getEvent();

            String taskId = event.getTaskId();
            String eventKey = event.getEventKey();
            String userId = eventMsg.getFrom().getUserid();

            System.out.println("👤 " + userId + " 点击了按钮: " + eventKey);
            System.out.println("📋 EventPayload里的taskId: " + taskId);

            // 🔥 从 BaseMessage 中获取 response_url（在 EventMessage 中可能没有）
            // 我们需要从原始的 BaseMessage 里取，或者从 EventMessage 的扩展字段取

            // 方式1：如果 EventMessage 有 getResponseUrl() 方法
            // String responseUrl = eventMsg.getResponseUrl();

            // 方式2：从扩展字段取（临时方案）
            String responseUrl = null;
            try {
                // 打印完整事件，看看 response_url 在哪里
                String json = JsonSupport.getObjectMapper().writeValueAsString(eventMsg);
                System.out.println("📩 完整事件 JSON: " + json);

                // 解析 response_url
                JsonNode root = JsonSupport.getObjectMapper().readTree(json);
                if (root.has("response_url")) {
                    responseUrl = root.get("response_url").asText();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if(responseUrl == null){
                String msgId = eventMsg.getMsgId();
                responseUrl = responseUrlCache.get(msgId);
                System.out.println("📤 从缓存获取 response_url: " + responseUrl);
            }
            if (responseUrl == null || responseUrl.isEmpty()) {
                System.out.println("⚠️ 没有 response_url，尝试使用 SDK 更新");
                // 降级方案
                Map<String, Object> updatedCard = buildUpdatedCard(eventKey, userId, taskId);
                updatedCard.put("task_id", taskId);
                client.updateTemplateCard(frame, updatedCard, null);
                return;
            }

            System.out.println("📤 response_url: " + responseUrl);

            // 构建更新卡片
            Map<String, Object> updatedCard = buildUpdatedCard(eventKey, userId, taskId);
            updatedCard.put("task_id", taskId);

            // 构建请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("response_type", "update_template_card");
            requestBody.put("template_card", updatedCard);

            // 发送 HTTP POST 请求
            sendHttpPost(responseUrl, requestBody);

            System.out.println("✅ 卡片已通过 HTTP 更新");

        } catch (Exception e) {
            System.err.println("❌ 处理卡片事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendHttpPost(String url, Map<String, Object> body) throws Exception {
        OkHttpClient client = new OkHttpClient();

        String json = JsonSupport.getObjectMapper().writeValueAsString(body);
        System.out.println("?? HTTP 请求体: " + json);

        RequestBody requestBody = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("?? HTTP 响应: " + responseBody);
            if (!response.isSuccessful()) {
                System.err.println("? HTTP 请求失败: " + response.code());
            }
        }
    }
    // 新增辅助方法：构建更新请求体
    private static Map<String, Object> buildUpdateBody(Map<String, Object> templateCard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("response_type", "update_template_card");
        body.put("template_card", new LinkedHashMap<>(templateCard));
        return body;
    }

    /**
     * 构建更新后的卡片（根据按钮点击结果）
     */
    private static Map<String, Object> buildUpdatedCard(String buttonKey, String userId, String taskId) {
        Map<String, Object> card = new LinkedHashMap<>();

        card.put("card_type", "text_notice");

        // 来源
        Map<String, String> source = new LinkedHashMap<>();
        source.put("icon_url", "https://wework.qpic.cn/wwpic/xxx_icon");
        source.put("desc", "来自智能助手");
        card.put("source", source);

        // 主标题 - 显示处理结果 + taskId
        Map<String, String> mainTitle = new LinkedHashMap<>();
        boolean isConfirm = "confirm".equals(buttonKey);
        String status = isConfirm ? "✅ 已确认处理" : "❌ 已驳回";
        mainTitle.put("title", status);
        mainTitle.put("desc", "taskId: " + taskId + " | 操作人: " + userId);  // 🔥 显示 taskId
        card.put("main_title", mainTitle);

        // 正文
        Map<String, String> emphasis = new LinkedHashMap<>();
        emphasis.put("title", "工单 #12345");
        emphasis.put("desc", "已由 " + userId + " 处理完成，此卡片已关闭");
        card.put("emphasis_content", emphasis);

        // 子标题
        List<Map<String, String>> subTitle = new ArrayList<>();
        Map<String, String> subItem = new LinkedHashMap<>();
        subItem.put("text", "🔒 卡片已关闭，无需进一步操作");
        subTitle.add(subItem);
        card.put("sub_title_text", subTitle);

        // 不传 button_list
        // task_id 由调用方设置

        return card;
    }

    /**
     * 从事件中提取卡片数据（根据实际SDK结构调整）
     */
    private static Map<String, Object> extractEventData(EventMessage eventMsg) {
        Map<String, Object> data = new LinkedHashMap<>();
        // 方式1：通过EventPayload中的template_card字段获取
        // 方式2：通过extensions获取
        // 这里根据实际消息结构解析，你的EventMessage可能包含getTemplateCard()方法
        // 如果SDK还没有封装好，可以先打印完整事件JSON调试
        data.put("key", "confirm");      // 示例
        data.put("task_id", "task_xxx");
        return data;
    }
}