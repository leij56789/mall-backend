package com.mall.controller;

import com.mall.pay.dto.WrapFormRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payment/form")
@RequiredArgsConstructor
public class PaymentFormWrapController {

    /**
     * 将支付宝表单包装成完整的 HTML 页面
     * <p>
     * 使用场景：
     * 1. 前端收到了支付宝返回的 payUrl（表单字符串），但需要在浏览器中打开
     * 2. 前端可以把表单传回后端，包装成完整 HTML 页面返回
     *
     * @param request 包含表单字符串的请求
     * @return 完整的 HTML 页面
     */
    @PostMapping(value = "/wrap", produces = MediaType.TEXT_HTML_VALUE)
    public String wrapForm(@RequestBody WrapFormRequest request) {
        String formHtml = request.getFormHtml();
        String paymentId = request.getPaymentId();

        log.info("包装支付表单: paymentId={}, formLength={}", paymentId, formHtml != null ? formHtml.length() : 0);

        if (formHtml == null || formHtml.isEmpty()) {
            return buildErrorPage("表单内容为空", "请确认支付表单数据是否正确");
        }

        // 如果是转义后的表单，需要反转义（如果前端传的是转义后的字符串）
        // String decodedForm = StringEscapeUtils.unescapeJava(formHtml);

        return buildHtmlPage(formHtml, paymentId);
    }

    /**
     * 构建完整的 HTML 页面
     */
    private String buildHtmlPage(String formHtml, String paymentId) {
        return "<!DOCTYPE html>\\n" +
                "<html>\\n" +
                "<head>\\n" +
                "    <meta charset=\"UTF-8\">\\n" +
                "    <title>跳转支付宝支付</title>\\n" +
                "    <style>\\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\\n" +
                "        body {\\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif;\\n" +
                "            display: flex;\\n" +
                "            justify-content: center;\\n" +
                "            align-items: center;\\n" +
                "            min-height: 100vh;\\n" +
                "            margin: 0;\\n" +
                "            background: #f5f5f5;\\n" +
                "        }\\n" +
                "        .container {\\n" +
                "            background: #fff;\\n" +
                "            border-radius: 12px;\\n" +
                "            padding: 40px;\\n" +
                "            max-width: 500px;\\n" +
                "            width: 100%;\\n" +
                "            box-shadow: 0 2px 12px rgba(0,0,0,0.1);\\n" +
                "            text-align: center;\\n" +
                "        }\\n" +
                "        .title { font-size: 20px; margin-bottom: 8px; color: #333; }\\n" +
                "        .subtitle { font-size: 14px; color: #999; margin-bottom: 20px; }\\n" +
                "        .spinner {\\n" +
                "            display: inline-block;\\n" +
                "            width: 40px;\\n" +
                "            height: 40px;\\n" +
                "            border: 4px solid #e8e8e8;\\n" +
                "            border-top-color: #1677ff;\\n" +
                "            border-radius: 50%;\\n" +
                "            animation: spin 0.8s linear infinite;\\n" +
                "        }\\n" +
                "        @keyframes spin {\\n" +
                "            to { transform: rotate(360deg); }\\n" +
                "        }\\n" +
                "        .payment-form { margin-top: 20px; }\\n" +
                "        .btn-fallback {\\n" +
                "            margin-top: 16px;\\n" +
                "            padding: 10px 30px;\\n" +
                "            background: #1677ff;\\n" +
                "            color: #fff;\\n" +
                "            border: none;\\n" +
                "            border-radius: 8px;\\n" +
                "            font-size: 16px;\\n" +
                "            cursor: pointer;\\n" +
                "        }\\n" +
                "        .btn-fallback:hover { background: #0958d9; }\\n" +
                "        .payment-id { font-size: 12px; color: #ccc; margin-top: 20px; }\\n" +
                "    </style>\\n" +
                "</head>\\n" +
                "<body>\\n" +
                "    <div class=\"container\">\\n" +
                "        <div class=\"title\">💳 跳转支付宝收银台</div>\\n" +
                "        <div class=\"subtitle\">订单号: " + (paymentId != null ? paymentId : "未知") + "</div>\\n" +
                "        <div class=\"spinner\"></div>\\n" +
                "        <div style=\"margin: 12px 0 8px; color: #666; font-size: 14px;\">正在跳转支付宝，请稍候...</div>\\n" +
                "        <div style=\"font-size: 12px; color: #999;\">如果页面没有自动跳转，请点击下方按钮</div>\\n" +
                "        <div class=\"payment-form\">" + formHtml + "</div>\\n" +
                "    </div>\\n" +
                "</body>\\n" +
                "</html>";
    }

    private String buildErrorPage(String title, String message) {
        return "<!DOCTYPE html>\\n" +
                "<html>\\n" +
                "<head><meta charset=\"UTF-8\"><title>支付表单错误</title></head>\\n" +
                "<body style=\"font-family: sans-serif; text-align: center; padding: 50px;\">\\n" +
                "    <h1 style=\"color: #e74c3c;\">❌ " + title + "</h1>\\n" +
                "    <p>" + message + "</p>\\n" +
                "    <p style=\"color: #999; font-size: 14px;\">请返回重试</p>\\n" +
                "</body>\\n" +
                "</html>";
    }
}