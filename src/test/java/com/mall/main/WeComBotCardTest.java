package com.mall.main;

import io.github.cloudsen.ai.wecom.WeComAiBotClient;
import io.github.cloudsen.ai.wecom.WeComAiBotClientOptions;
import io.github.cloudsen.ai.wecom.model.*;
import io.github.cloudsen.ai.common.JsonSupport;
import okhttp3.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeComBotCardTest {

    // 缓存 msgId -> response_url
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

            if ("卡片".equals(content)) {
                String taskId = "task_" + System.currentTimeMillis();

                // 🔥 发送前先保存 taskId -> response_url
                String responseUrl = msg.getResponseUrl();
                if (responseUrl != null && !responseUrl.isEmpty()) {
                    responseUrlCache.put(taskId, responseUrl);
                    System.out.println("💾 保存 response_url, taskId: " + taskId + " -> " + responseUrl);
                }

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
            EventPayload event = eventMsg.getEvent();

            // 🔥 用 taskId 去缓存里取 response_url
            String taskId = event.getTaskId();
            String responseUrl = responseUrlCache.get(taskId);

            System.out.println("📩 事件 taskId: " + taskId);
            System.out.println("📤 从缓存获取 response_url: " + responseUrl);

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
     * 构建带按钮的卡片
     */
    private static Map<String, Object> buildButtonCard(String taskId) {
        Map<String, Object> card = new LinkedHashMap<>();

        // card_type 固定为 button_interaction
        card.put("card_type", "button_interaction");

        // ===== source =====
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("icon_url", "https://wework.qpic.cn/wwpic/252813_jOfDHtcISzuodLa_1629280209/0");
        source.put("desc", "企业微信");
        source.put("desc_color", 0);  // 0=灰色, 1=黑色, 2=蓝色
        card.put("source", source);

        // ===== action_menu（右上角菜单） =====
        Map<String, Object> actionMenu = new LinkedHashMap<>();
        actionMenu.put("desc", "消息气泡副交互辅助文本说明");
        List<Map<String, String>> actionList = new ArrayList<>();

        Map<String, String> action1 = new LinkedHashMap<>();
        action1.put("text", "接收推送");
        action1.put("key", "action_key1");
        actionList.add(action1);

        Map<String, String> action2 = new LinkedHashMap<>();
        action2.put("text", "不再推送");
        action2.put("key", "action_key2");
        actionList.add(action2);

        actionMenu.put("action_list", actionList);
        card.put("action_menu", actionMenu);

        // ===== main_title =====
        Map<String, String> mainTitle = new LinkedHashMap<>();
        mainTitle.put("title", "欢迎使用企业微信");
        mainTitle.put("desc", "您的好友正在邀请您加入企业微信");
        card.put("main_title", mainTitle);

        // ===== quote_area（引用区） =====
        Map<String, Object> quoteArea = new LinkedHashMap<>();
        quoteArea.put("type", 1);  // 1=文本型, 2=图片型
        quoteArea.put("url", "https://work.weixin.qq.com/?from=openApi");
        quoteArea.put("title", "引用文本标题");
        quoteArea.put("quote_text", "Jack：企业微信真的很好用~\\nBalian：超级好的一款软件！");
        card.put("quote_area", quoteArea);

        // ===== sub_title_text =====
        card.put("sub_title_text", "下载企业微信还能抢红包！");

        // ===== horizontal_content_list =====
        List<Map<String, String>> horizontalContentList = new ArrayList<>();
        Map<String, String> hItem = new LinkedHashMap<>();
        hItem.put("keyname", "邀请人");
        hItem.put("value", "张三");
        horizontalContentList.add(hItem);

        // 可以继续添加更多
        // Map<String, String> hItem2 = new LinkedHashMap<>();
        // hItem2.put("keyname", "部门");
        // hItem2.put("value", "技术部");
        // horizontalContentList.add(hItem2);

        card.put("horizontal_content_list", horizontalContentList);

        // ===== button_selection（下拉选择器） =====
        Map<String, Object> buttonSelection = new LinkedHashMap<>();
        buttonSelection.put("question_key", "button_selection_key1");
        buttonSelection.put("title", "你的身份");
        buttonSelection.put("disable", false);

        List<Map<String, String>> optionList = new ArrayList<>();
        Map<String, String> option1 = new LinkedHashMap<>();
        option1.put("id", "button_selection_id1");
        option1.put("text", "企业负责人");
        optionList.add(option1);

        Map<String, String> option2 = new LinkedHashMap<>();
        option2.put("id", "button_selection_id2");
        option2.put("text", "企业用户");
        optionList.add(option2);

        buttonSelection.put("option_list", optionList);
        buttonSelection.put("selected_id", "button_selection_id1");
        card.put("button_selection", buttonSelection);

        // ===== button_list（按钮列表） =====
        List<Map<String, Object>> buttonList = new ArrayList<>();

        Map<String, Object> btn1 = new LinkedHashMap<>();
        btn1.put("text", "按钮1");
        btn1.put("style", 4);   // 1=蓝色(主要), 2=灰色(次要), 3=红色(警告), 4=绿色(成功)
        btn1.put("key", "BUTTONKEYONE");
        buttonList.add(btn1);

        Map<String, Object> btn2 = new LinkedHashMap<>();
        btn2.put("text", "按钮2");
        btn2.put("style", 1);
        btn2.put("key", "BUTTONKEYTWO");
        buttonList.add(btn2);

        card.put("button_list", buttonList);

        // ===== card_action（整体点击跳转） =====
        Map<String, Object> cardAction = new LinkedHashMap<>();
        cardAction.put("type", 1);      // 1=跳转URL, 2=跳转小程序
        cardAction.put("url", "https://work.weixin.qq.com/?from=openApi");
        card.put("card_action", cardAction);

        // ===== task_id =====
        card.put("task_id", taskId);

        return card;
    }

    /**
     * 构建更新后的卡片（纯文本）
     */
    private static Map<String, Object> buildUpdatedCard(String eventKey, String userId, String taskId) {
        Map<String, Object> card = new LinkedHashMap<>();

        // 🔥 保持 card_type 为 button_interaction
        card.put("card_type", "button_interaction");

        // ===== source（建议保留） =====
        Map<String, String> source = new LinkedHashMap<>();
        source.put("icon_url", "https://wework.qpic.cn/wwpic/252813_jOfDHtcISzuodLa_1629280209/0");
        source.put("desc", "企业微信");
        card.put("source", source);

        // ===== main_title（必填） =====
        Map<String, String> mainTitle = new LinkedHashMap<>();
        boolean isConfirm = "confirm".equals(eventKey);
        String status = isConfirm ? "✅ 已确认处理" : "❌ 已驳回";
        mainTitle.put("title", status);
        mainTitle.put("desc", "操作人: " + userId);
        card.put("main_title", mainTitle);

        // ===== sub_title_text（可选，但为了丰富展示） =====
        card.put("sub_title_text", "工单 #" + taskId.substring(taskId.lastIndexOf("_") + 1) + " 已处理完成");

        // ===== horizontal_content_list（可选，展示更多信息） =====
        List<Map<String, String>> horizontalContentList = new ArrayList<>();
        Map<String, String> hItem1 = new LinkedHashMap<>();
        hItem1.put("keyname", "处理人");
        hItem1.put("value", userId);
        horizontalContentList.add(hItem1);

        Map<String, String> hItem2 = new LinkedHashMap<>();
        hItem2.put("keyname", "处理结果");
        hItem2.put("value", isConfirm ? "已确认" : "已驳回");
        horizontalContentList.add(hItem2);

        Map<String, String> hItem3 = new LinkedHashMap<>();
        hItem3.put("keyname", "处理时间");
        hItem3.put("value", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        horizontalContentList.add(hItem3);
        card.put("horizontal_content_list", horizontalContentList);

        // ===== button_list（必填，至少一个按钮） =====
        List<Map<String, Object>> buttonList = new ArrayList<>();
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("text", "✅ 已处理");
        btn.put("style", 2);  // 灰色
        btn.put("key", "done");
        buttonList.add(btn);
        card.put("button_list", buttonList);

        // ===== task_id（必填） =====
        card.put("task_id", taskId);

        return card;
    }

    /**
     * 处理卡片点击事件 - 使用 HTTP 方式更新
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
            System.out.println("📋 taskId: " + taskId);

            // 构建更新后的卡片
            Map<String, Object> updatedCard = buildUpdatedCard(eventKey, userId, taskId);

            // 🔥 构建更新响应体
            Map<String, Object> updateResponse = new LinkedHashMap<>();
            updateResponse.put("response_type", "update_template_card");
            updateResponse.put("template_card", updatedCard);

            // 🔥 打印完整 JSON 确认结构
            try {
                String json = JsonSupport.getObjectMapper().writeValueAsString(updateResponse);
                System.out.println("📤 更新请求 JSON: " + json);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 🔥 发送更新
            String reqId = frame.getReqId();
            System.out.println("📤 使用 req_id: " + reqId);

            client.reply(reqId, updateResponse, "aibot_respond_update_msg");

            System.out.println("✅ 已发送卡片更新响应");

        } catch (Exception e) {
            System.err.println("❌ 处理卡片事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送 HTTP POST 请求
     */
    private static String sendHttpPost(String url, Map<String, Object> body) throws Exception {
        OkHttpClient client = new OkHttpClient();

        String json = JsonSupport.getObjectMapper().writeValueAsString(body);
        System.out.println("📤 HTTP 请求体: " + json);

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
            if (!response.isSuccessful()) {
                throw new Exception("HTTP 请求失败: " + response.code() + ", " + responseBody);
            }
            return responseBody;
        }
    }
}