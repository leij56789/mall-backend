好的，以下是 alipay.data.dataservice.bill.downloadurl.query（查询对账单下载地址）的投喂信息模板：

---

📌 投喂信息分类模板

1. 接口基本信息

· 接口名称：
· 接口描述：
· 请求地址：
· 产品码（如有）：

2. 公共请求参数

参数名 类型 必填 最大长度 描述 示例值
app_id String 是 32 支付宝分配给开发者的应用ID 2014072300007148
method String 是 128 接口名称 alipay.data.dataservice.bill.downloadurl.query
format String 否 40 仅支持JSON JSON
charset String 是 10 请求使用的编码格式 utf-8
sign_type String 是 10 签名算法类型 RSA2
sign String 是 344 请求参数的签名串 -
timestamp String 是 19 发送请求的时间 2014-07-24 03:07:50
version String 是 3 接口版本，固定为1.0 1.0
app_auth_token String 否 40 应用授权令牌（可选） -
biz_content String 是 - 请求参数的集合 -

3. 业务请求参数（biz_content）

参数名 类型 必填 最大长度 描述 示例值
bill_type String 是 - 账单类型，商户通过接口或商户经开放平台授权后其所属服务方对账单 trade（交易账单）
bill_date String 是 15 账单日期，格式：yyyy-MM-dd 2016-04-05

4. 响应参数

参数名 类型 描述 示例值
bill_download_url String 对账单文件的下载地址 http://dw.alipay.com/...
bill_type String 账单类型 trade
bill_date String 账单日期 2016-04-05

5. 响应示例

成功示例：

```json
{
    "alipay_data_dataservice_bill_downloadurl_query_response": {
        "code": "10000",
        "msg": "Success",
        "bill_download_url": "http://dw.alipay.com/...",
        "bill_type": "trade",
        "bill_date": "2016-04-05"
    },
    "sign": "xxx"
}
```

失败示例：

```json
{
    "alipay_data_dataservice_bill_downloadurl_query_response": {
        "code": "40004",
        "msg": "Business Failed",
        "sub_code": "ACQ.INVALID_PARAMETER",
        "sub_msg": "参数无效"
    },
    "sign": "xxx"
}
```

6. 业务错误码

错误码 错误描述 解决方案


7. SDK 调用示例（如有）

```java
// 代码片段
```

8. 其他注意事项（如有）

· 对账单文件格式为 CSV
· 账单下载地址有效期为 24 小时
· 账单文件大小可能较大

使用说明

1. 复制上面的模板
2. 在支付宝开放平台找到 alipay.data.dataservice.bill.downloadurl.query 的官方文档
3. 按分类填入对应内容
4. 一次性将完整内容发给我
5. 发送后回复 “投喂结束”

收到后我会生成对账单查询的完整实现代码。👌

orvyau1103@sandbox.com
INSERT INTO orders (
order_no, user_id, book_id, quantity, total_amount, status,
address, expire_time, order_type, created_at, updated_at
) VALUES (
'TEST_ORDER_20260824_003',
1,                     -- 用户ID（testuser）
1,                     -- 图书ID（确保存在）
1,                     -- 数量
10,                  -- 金额
0,                     -- 状态：0=待支付
'北京市朝阳区测试路123号',
DATE_ADD(NOW(), INTERVAL 300 MINUTE),  -- 30分钟后过期
0,                     -- 订单类型：0=普通
NOW(),
NOW()
);