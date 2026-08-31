package com.mall.dto.message;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DingTalkMessage {
    private String msgtype;
    private TextContent text;
    private MarkdownContent markdown;
    private AtInfo at;

    @Data
    @Builder
    public static class TextContent {
        private String content;
    }

    @Data
    @Builder
    public static class MarkdownContent {
        private String title;
        private String text;
    }

    @Data
    @Builder
    public static class AtInfo {
        private List<String> atMobiles;
        private boolean isAtAll;
    }

    // 便捷构造方法
    public static DingTalkMessage text(String content) {
        return DingTalkMessage.builder()
                .msgtype("text")
                .text(TextContent.builder().content(content).build())
                .build();
    }

    public static DingTalkMessage markdown(String title, String content) {
        return DingTalkMessage.builder()
                .msgtype("markdown")
                .markdown(MarkdownContent.builder()
                        .title(title)
                        .text(content)
                        .build())
                .build();
    }
}