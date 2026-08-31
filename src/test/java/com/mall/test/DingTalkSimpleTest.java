package com.mall.test;

import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DingTalkSimpleTest {
//https://oapi.dingtalk.com/robot/send?access_token=596cf58a0c070f77c5edbd9b5c1cd6ec84d6f37bb30c7e6ac8eebc7b97df68c8
    // ===== 替换为你的新值 =====
    private static final String SECRET = "SECed9bd42d0ef90d4f3da8849609a6fe08827b4a55cc24673f266ee142bb56d4d9";
    private static final String ACCESS_TOKEN = "596cf58a0c070f77c5edbd9b5c1cd6ec84d6f37bb30c7e6ac8eebc7b97df68c8";

    public static void main(String[] args) throws Exception {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + SECRET;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = Base64.getEncoder().encodeToString(signBytes);
        String encodedSign = URLEncoder.encode(sign, StandardCharsets.UTF_8.name());

        String url = "https://oapi.dingtalk.com/robot/send?access_token=" + ACCESS_TOKEN
                + "&timestamp=" + timestamp
                + "&sign=" + encodedSign;

        System.out.println("URL: " + url);

        String json = "{\"msgtype\":\"text\",\"text\":{\"content\":\"第五条测试消息\"}}";

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
            System.out.println("响应: " + result);
            if (result.contains("\"errcode\":0")) {
                System.out.println("✅ 成功！钉钉群应该收到消息了");
            } else {
                System.out.println("❌ 失败，请检查 SECRET 和 ACCESS_TOKEN 是否复制正确");
            }
        }
    }
}