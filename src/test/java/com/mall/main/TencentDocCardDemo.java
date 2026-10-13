package com.mall.main;

import io.github.cloudsen.ai.wecom.WeComAiBotClient;
import io.github.cloudsen.ai.wecom.WeComAiBotClientOptions;
import io.github.cloudsen.ai.wecom.model.BaseMessage;
import io.github.cloudsen.ai.wecom.model.WsFrame;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 独立测试：发送可点击的腾讯文档收集表卡片
 */
public class TencentDocCardDemo {

    // 你的腾讯文档收集表链接
    private static final String FORM_URL = "https://docs.qq.com/form/page/DZEdraER0U0JSanph";

    public static void main(String[] args) {
        String botId = "aibdjc5jFVoLZsk71eNlDzOGJsqaFXkZ5pJ";
        String botSecret = "TYNrZd6fEBqUFPEQVkWE4IPwZFP9qgASJyttkjaMZB6";

        WeComAiBotClientOptions options = WeComAiBotClientOptions.builder(botId, botSecret).build();
        WeComAiBotClient client = new WeComAiBotClient(options);

        // 监听文本消息
        client.onTextMessage(frame -> {
            BaseMessage msg = frame.getBody();
            String content = msg.getText().getContent();

            if ("登记".equals(content) || "民宿".equals(content)) {
                // 发送可点击的腾讯文档卡片
                Map<String, Object> card = buildLinkCard();
                client.replyTemplateCard(frame, card, null);
                System.out.println("? 已发送腾讯文档卡片");
            } else {
                client.replySimpleText(frame, "发送「登记」获取民宿推广登记表");
            }
        });

        client.onAuthenticated(() -> System.out.println("? 认证成功"));
        client.connect();
        System.out.println("?? 机器人已启动，发送「登记」测试");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            client.disconnect();
        }
    }

    /**
     * 构建可点击跳转的图文展示型卡片
     */
    private static Map<String, Object> buildLinkCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("card_type", "news_notice");

        // 来源
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("icon_url", "https://wework.qpic.cn/wwpic/252813_jOfDHtcISzuodLa_1629280209/0");
        source.put("desc", "腾讯文档");
        card.put("source", source);

        // 主标题
        Map<String, String> mainTitle = new LinkedHashMap<>();
        mainTitle.put("title", "📋 民宿推广登记");
        mainTitle.put("desc", "点击填写收集表");
        card.put("main_title", mainTitle);

        // 🔥 必填：卡片图片（news_notice 必须有图）
        Map<String, Object> cardImage = new LinkedHashMap<>();
        cardImage.put("url", "https://wework.qpic.cn/wwpic/252813_jOfDHtcISzuodLa_1629280209/0");
        cardImage.put("aspect_ratio", 2.25);
        card.put("card_image", cardImage);

        // 二级文本
        card.put("sub_title_text", "填写后信息将自动汇总到表格");

        // 卡片整体点击跳转
        Map<String, Object> cardAction = new LinkedHashMap<>();
        cardAction.put("type", 1);
        cardAction.put("url", FORM_URL);
        card.put("card_action", cardAction);

        return card;
    }
}