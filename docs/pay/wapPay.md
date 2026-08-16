好的，以下是我根据你提供的官方文档整理成的 Markdown 格式文档：

---

支付宝手机网站支付接口 2.0（alipay.trade.wap.pay）

📌 1. 接口基本信息

项目 内容
接口名称 alipay.trade.wap.pay
接口描述 手机网站支付接口 2.0
请求地址 https://openapi.alipay.com/gateway.do
接口类型 页面跳转接口，生成用户访问支付宝的跳转链接或表单
产品码 QUICK_WAP_WAY
请求方式 POST / GET（推荐 POST）

📋 2. 公共请求参数

参数名 类型 必填 最大长度 描述 示例值
app_id String ✅ 是 32 支付宝分配给开发者的应用ID 2014072300007148
method String ✅ 是 128 接口名称 alipay.trade.wap.pay
format String ❌ 否 40 响应格式，仅支持 JSON JSON
return_url String ❌ 否 256 同步返回地址（HTTP/HTTPS） https://m.alipay.com/Gk8NF23
charset String ✅ 是 10 编码格式 utf-8
sign_type String ✅ 是 10 签名算法，推荐 RSA2 RSA2
sign String ✅ 是 344 请求参数的签名串 -
timestamp String ✅ 是 19 发送请求的时间 2014-07-24 03:07:50
version String ✅ 是 3 接口版本，固定为 1.0 1.0
notify_url String ❌ 否 256 异步通知地址（HTTP/HTTPS） http://api.test.alipay.net/...

📦 3. 业务请求参数（biz_content）

参数名 类型 必填 最大长度 描述 示例值
out_trade_no String ✅ 是 64 商户网站唯一订单号 70501111111S001111119
total_amount Price ✅ 是 9 订单总金额（元），精确到小数点后两位，范围 [0.01, 100000000] 9.00
subject String ✅ 是 256 订单标题（不可使用 /,=,& 等特殊字符） 大乐透
product_code String ✅ 是 64 销售产品码，手机网站支付为 QUICK_WAP_WAY QUICK_WAP_WAY
auth_token String ❌ 否 40 用户授权接口标识 appopenBb64d181d0146481ab6a762c00714cC27
quit_url String ❌ 否 400 用户付款中途退出返回商户网站的地址 http://www.taobao.com/product/113714.html
goods_detail GoodsDetail[] ❌ 否 - 订单包含的商品列表信息（JSON 格式） 见 SDK 示例
time_expire String ❌ 否 32 订单绝对超时时间，格式 yyyy-MM-dd HH:mm:ss，范围 1m~15d 2016-12-31 10:05:00
extend_params ExtendParams ❌ 否 - 业务扩展参数 见 SDK 示例
business_params String ❌ 否 512 商户传入业务信息（JSON 格式） {"mc_create_trade_ip":"127.0.0.1"}
passback_params String ❌ 否 512 公用回传参数（需 URLEncode） merchantBizType%3d3C%26merchantBizNo%3d2016010101111
merchant_order_no String ❌ 否 32 商户原始订单号 20161008001
ext_user_info ExtUserInfo ❌ 否 - 外部指定买家信息 见 SDK 示例

✅ 4. 响应参数

4.1 业务响应参数

参数名 类型 必填 描述 示例值
pageRedirectionData String ✅ 是 跳转页面数据（HTML 表单或 URL） 见下方示例

4.2 成功响应示例（HTML 表单）

```html
<form name="punchout_form" method="post" action="https://openapi.alipay.com/gateway.do?charset=UTF-8&method=alipay.trade.wap.pay&format=json&sign=ERITJKEIJKJHKKKKKKKHJEREEEEEEEEEEE&version=1.0&app_id=2017060101317939&sign_type=RSA2&timestamp=2014-07-24+03%3A07%3A50">
    <input type="hidden" name="biz_content" value="{&quot;time_expire&quot;:&quot;2016-12-31 10:05:00&quot;,&quot;subject&quot;:&quot;大乐透&quot;,&quot;product_code&quot;:&quot;QUICK_WAP_WAY&quot;,&quot;out_trade_no&quot;:&quot;70501111111S001111119&quot;,&quot;total_amount&quot;:&quot;9.00&quot;}">
    <input type="submit" value="立即支付" style="display:none">
</form>
<script>document.forms[0].submit();</script>
```

4.3 失败响应示例

```json
{
    "alipay_trade_wap_pay_response": {
        "code": "40004",
        "msg": "Business Failed",
        "sub_code": "ACQ.INVALID_PARAMETER",
        "sub_msg": "参数无效"
    },
    "sign": "xxx"
}
```

❌ 5. 业务错误码

错误码 错误描述 解决方案
ACQ.ACCESS_FORBIDDEN 无权限使用接口 未签约条码支付或合同已到期
ACQ.CONTEXT_INCONSISTENT 交易信息被篡改 更换商家订单号后重新发起
ACQ.EXIST_FORBIDDEN_WORD 订单信息中包含违禁词 修改订单信息后重新发起
ACQ.INVALID_PARAMETER 参数无效 检查请求参数，修改后重新发起
ACQ.PARTNER_ERROR 应用 APP_ID 填写错误 联系支付宝小二确认 APP_ID 状态
ACQ.PAYMENT_REQUEST_HAS_RISK 支付有风险 更换其它付款方式
ACQ.RISK_MERCHANT_IP_NOT_EXIST 当前交易未传入 IP 信息 传入用户 IP 信息后重试
ACQ.SYSTEM_ERROR 系统异常 立即调用查询 API 确认订单状态，多次失败联系支付宝客服
ACQ.TOTAL_FEE_EXCEED 订单总金额不在允许范围内 修改订单金额后重试
ACQ.TRADE_HAS_CLOSE 交易已经关闭 更换商家订单号后重新发起
ACQ.TRADE_HAS_SUCCESS 交易已被支付 确认是否当前买家，如果是则视为成功，否则更换订单号重试

🔔 6. 异步通知类型（notify_type）

通知类型 描述 默认开启
tradeStatus.TRADE_SUCCESS 支付成功 ✅ 开启（1）
tradeStatus.TRADE_CLOSED 交易关闭 ✅ 开启（1）
tradeStatus.TRADE_FINISHED 交易完结 ✅ 开启（1）
tradeStatus.WAIT_BUYER_PAY 交易创建 ❌ 关闭（0）

说明

· TRADE_SUCCESS：支付成功，必须处理的异步通知，用于更新订单状态。
· TRADE_CLOSED：交易关闭，通常表示订单超时未支付或用户主动关闭。
· TRADE_FINISHED：交易完结，表示订单已完成且不可退款（与 TRADE_SUCCESS 类似，但语义不同）。
· WAIT_BUYER_PAY：交易创建，默认不触发通知，表示用户已进入支付页面但尚未完成支付。

⚠️ 注意：WAIT_BUYER_PAY 默认关闭，如需开启请联系支付宝技术支持。

💻 7. SDK 调用示例

示例代码（Java）

```java
package com.java.sdk.demo;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.domain.ExtUserInfo;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.domain.ExtendParams;
import com.alipay.api.domain.GoodsDetail;
import com.alipay.api.request.AlipayTradeWapPayRequest;

import java.util.ArrayList;
import java.util.List;

public class AlipayTradeWapPay {

    public static void main(String[] args) throws AlipayApiException {
        // 初始化 SDK
        AlipayClient alipayClient = new DefaultAlipayClient(getAlipayConfig());

        // 构造请求参数
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();

        // ===== 必填参数 =====
        model.setOutTradeNo("70501111111S001111119");
        model.setTotalAmount("9.00");
        model.setSubject("大乐透");
        model.setProductCode("QUICK_WAP_WAY");

        // ===== 可选参数 =====
        model.setAuthToken("appopenBb64d181d0146481ab6a762c00714cC27");
        model.setQuitUrl("http://www.taobao.com/product/113714.html");
        model.setTimeExpire("2016-12-31 10:05:00");

        // 商品列表
        List<GoodsDetail> goodsDetail = new ArrayList<>();
        GoodsDetail goods = new GoodsDetail();
        goods.setGoodsName("ipad");
        goods.setAlipayGoodsId("20010001");
        goods.setQuantity(1L);
        goods.setPrice("2000");
        goods.setGoodsId("apple-01");
        goods.setGoodsCategory("34543238");
        goods.setBody("特价手机");
        goods.setShowUrl("http://www.alipay.com/xxx.jpg");
        goodsDetail.add(goods);
        model.setGoodsDetail(goodsDetail);

        // 业务扩展参数
        ExtendParams extendParams = new ExtendParams();
        extendParams.setSysServiceProviderId("2088511833207846");
        extendParams.setHbFqSellerPercent("100");
        extendParams.setHbFqNum("3");
        model.setExtendParams(extendParams);

        // 业务信息
        model.setBusinessParams("{\"mc_create_trade_ip\":\"127.0.0.1\"}");
        model.setPassbackParams("merchantBizType%3d3C%26merchantBizNo%3d2016010101111");

        // 外部指定买家
        ExtUserInfo extUserInfo = new ExtUserInfo();
        extUserInfo.setCertType("IDENTITY_CARD");
        extUserInfo.setCertNo("362334768769238881");
        extUserInfo.setName("李明");
        extUserInfo.setMobile("16587658765");
        model.setExtUserInfo(extUserInfo);

        request.setBizModel(model);

        // ===== 执行请求（POST 方式，返回 HTML 表单） =====
        AlipayTradeWapPayResponse response = alipayClient.pageExecute(request, "POST");

        // 获取表单 HTML
        String pageRedirectionData = response.getBody();
        System.out.println(pageRedirectionData);

        if (response.isSuccess()) {
            System.out.println("调用成功");
        } else {
            System.out.println("调用失败");
        }
    }

    private static AlipayConfig getAlipayConfig() {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl("https://openapi.alipay.com/gateway.do");
        config.setAppId("2019091767145019");
        config.setPrivateKey("MIIEvQIBADANB...");
        config.setAlipayPublicKey("MIIBIjANBg...");
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        return config;
    }
}
```

📎 8. 相关链接

· 支付宝开放平台：https://open.alipay.com
· 沙箱控制台：https://openhome.alipay.com/develop/sandbox/app
· 错误码查询：https://opensupport.alipay.com/support/helpcenter

📝 9. 重要说明

要点 说明
产品码 必须为 QUICK_WAP_WAY
请求方式 推荐使用 POST 方式获取 HTML 表单，前端自动提交；GET 方式返回跳转 URL
金额范围 [0.01, 100000000]，精确到小数点后两位
超时时间格式 yyyy-MM-dd HH:mm:ss，范围 1m ~ 15d
异步通知 支付结果以 notify_url 收到的异步通知为准，return_url 仅作为同步跳转提示
敏感字符 subject 中不可使用 /、=、& 等特殊字符
回传参数 passback_params 必须进行 URLEncode 编码后再发送

---

以上是完整的 alipay.trade.wap.pay 接口文档整理。如有遗漏或需要补充的内容，请告诉我。📄