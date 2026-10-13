package com.mall.pay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AlipayFormToHtml {

    public static void main(String[] args) throws IOException {
        // 输入文件：存放支付宝返回的原始字符串
        String inputPath = "D:/Work/Workspace/mall-backend - 副本/html/rawForm.txt";
        // 输出文件：生成的 HTML
        String outputPath = "D:/Work/Workspace/mall-backend - 副本/html/pay.html";

        // 1. 从文件读取 rawForm
        String rawForm = new String(
                Files.readAllBytes(Paths.get(inputPath)),
                StandardCharsets.UTF_8
        );
        System.out.println("读取 rawForm 成功，长度=" + rawForm.length());

        // 2. 转成 HTML
        String html = buildHtml(rawForm);

        // 3. 写出
        Files.write(Paths.get(outputPath), html.getBytes(StandardCharsets.UTF_8));
        System.out.println("已生成：" + Paths.get(outputPath).toAbsolutePath());
    }

    private static String buildHtml(String rawForm) {
        // 1. 还原 \\n 为换行
        String form = rawForm.replace("\\n", "\n");

        // 2. 还原 \" 为 "
        form = form.replace("\\\"", "\"");

        // 3. &quot; 还原为 "
        form = form.replace("&quot;", "\"");

        // 4. 处理 biz_content 的引号冲突：value="{"..."}" → value='{"..."}'
        form = form.replaceAll(
                "value=\"(\\{.*?\\})\"",
                "value='$1'"
        );

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>支付宝支付</title>\n" +
                "</head>\n" +
                "<body>\n" +
                form + "\n" +
                "</body>\n" +
                "</html>";
    }
}