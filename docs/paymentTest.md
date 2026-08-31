-- 插入测试订单（order_no 需唯一）
INSERT INTO orders (
order_no, user_id, book_id, quantity, total_amount, status, address, expire_time, order_type
) VALUES (
'TEST_ORDER_001',    -- 订单号
1,                   -- 用户ID（对应 testuser）
1,                   -- 图书ID（对应 Java核心技术）
1,                   -- 数量
99.00,               -- 金额
0,                   -- 状态：0=待支付
'北京市朝阳区测试路',  -- 地址
DATE_ADD(NOW(), INTERVAL 300 MINUTE),  -- 30分钟后过期
0                    -- 订单类型：0=普通订单
);
-- 如果你之前的订单号是 TEST_ORDER_001，可以直接复用它，让它生成新的 payment_id
-- 或者插入一条新的测试订单
INSERT INTO orders (order_no, user_id, book_id, quantity, total_amount, status, address, expire_time, order_type)
VALUES ('TEST_ORDER_002', 1, 1, 1, 0.01, 0, '北京市朝阳区测试路', DATE_ADD(NOW(), INTERVAL 30 MINUTE), 0);
INSERT INTO orders (
order_no,
user_id,
book_id,
quantity,
total_amount,
status,
address,
expire_time,
order_type,
created_at,
updated_at
) VALUES (
'TEST_WAP_AUDIT_HASH_003',          -- 订单号
1,                       -- 用户ID（对应 testuser）
1,                       -- 图书ID（对应 Java核心技术）
1,                       -- 数量
10.00,                    -- 金额（0.01元，方便测试）
0,                       -- 状态：0=待支付
'北京市朝阳区测试路123号', -- 地址
DATE_ADD(NOW(), INTERVAL 300 MINUTE), -- 30分钟后过期
0,                       -- 订单类型：0=普通订单
NOW(),
NOW()
);
select * from orders where order_no = 'TEST_WAP_AUDIT_HASH_003';
SELECT id, order_no, created_at
FROM orders
ORDER BY created_at DESC
LIMIT 10;