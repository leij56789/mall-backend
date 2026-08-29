-- ==========================================
-- 商城项目 - 完整建表语句
-- ==========================================

CREATE DATABASE IF NOT EXISTS mall;
USE mall;

-- ==========================================
-- 1. 用户表
-- ==========================================
CREATE TABLE `user` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `username` VARCHAR(40) NOT NULL UNIQUE,
                        `email` VARCHAR(60) NOT NULL UNIQUE,
                        `password` VARCHAR(100) NOT NULL,
                        `bio` TEXT,
                        `image` VARCHAR(200),
                        `address` VARCHAR(200),
                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX `idx_username` (`username`),
                        INDEX `idx_email` (`email`)
);

-- ==========================================
-- 2. 图书表
-- ==========================================
CREATE TABLE `book` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `isbn` VARCHAR(20) NOT NULL UNIQUE,
                        `name` VARCHAR(200) NOT NULL,
                        `author` VARCHAR(100),
                        `publisher` VARCHAR(100),
                        `price` DECIMAL(10,2) NOT NULL,
                        `stock` INT NOT NULL DEFAULT 0,
                        `category_id` BIGINT,
                        `description` TEXT,
                        `cover_image` VARCHAR(200),
                        `status` TINYINT DEFAULT 1 COMMENT '1上架 0下架',
                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX `idx_category` (`category_id`),
                        INDEX `idx_name` (`name`)
);
# 添加乐观锁 version
ALTER TABLE book ADD COLUMN version INT DEFAULT 0;
-- ==========================================
-- 3. 订单表
-- ==========================================
CREATE TABLE `orders` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                          `order_no` VARCHAR(32) NOT NULL UNIQUE,
                          `user_id` BIGINT NOT NULL,
                          `book_id` BIGINT NOT NULL,
                          `quantity` INT NOT NULL,
                          `total_amount` DECIMAL(10,2) NOT NULL,
                          `status` TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已完成',
                          `address` VARCHAR(200),
                          `expire_time` DATETIME,
                          `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          INDEX `idx_user_id` (`user_id`),
                          INDEX `idx_order_no` (`order_no`),
                          FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
-- 如果表已存在，添加字段
ALTER TABLE orders ADD COLUMN order_type TINYINT DEFAULT 0 COMMENT '订单类型：0-普通订单 1-秒杀订单';
-- ==========================================
-- 4. 购物车表
-- ==========================================
CREATE TABLE `cart` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `user_id` BIGINT NOT NULL,
                        `book_id` BIGINT NOT NULL,
                        `quantity` INT NOT NULL DEFAULT 1,
                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY `uk_user_book` (`user_id`, `book_id`),
                        FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY (`book_id`) REFERENCES `book`(`id`) ON DELETE CASCADE
);

-- ==========================================
-- 5. 分类表
-- ==========================================
CREATE TABLE `category` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                            `name` VARCHAR(50) NOT NULL,
                            `parent_id` BIGINT DEFAULT 0,
                            `sort_order` INT DEFAULT 0,
                            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            INDEX `idx_parent_id` (`parent_id`)
);
-- ==========================================
-- 消息日志表
-- ==========================================
CREATE TABLE `broker_message_log` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `order_id` BIGINT NOT NULL COMMENT '订单ID',
                                      `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID（业务幂等）',
                                      `exchange` VARCHAR(100) NOT NULL COMMENT 'MQ交换机',
                                      `routing_key` VARCHAR(100) NOT NULL COMMENT 'MQ路由键',
                                      `message_body` JSON NOT NULL COMMENT '消息体JSON（快照数据）',
                                      `delay_time` INT NOT NULL COMMENT '延迟时间(毫秒)',
                                      `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待发送 1-已发送 2-发送失败 3-最终失败',
                                      `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
                                      `max_retry` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
                                      `next_retry_time` DATETIME NOT NULL COMMENT '下次重试时间',
                                      `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '最后一次错误信息',
                                      `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_message_id` (`message_id`),
                                      KEY `idx_status_next_retry` (`status`, `next_retry_time`),
                                      KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息日志表（本地消息表）';
ALTER TABLE broker_message_log
    ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT '用户ID（秒杀/订单关联）',
    ADD COLUMN book_id BIGINT DEFAULT NULL COMMENT '商品ID（秒杀/订单关联）',
    ADD INDEX idx_user_id (user_id),
    ADD INDEX idx_book_id (book_id);
ALTER TABLE broker_message_log MODIFY order_id BIGINT NULL;
ALTER TABLE broker_message_log MODIFY delay_time INT NULL;
-- 秒杀商品表
CREATE TABLE seckill_book (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              book_id BIGINT NOT NULL,
                              seckill_price DECIMAL(10,2) NOT NULL,
                              stock INT NOT NULL,
                              start_time DATETIME NOT NULL,
                              end_time DATETIME NOT NULL,
                              version INT DEFAULT 0,
                              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                              INDEX idx_book_id (book_id)
);

-- 秒杀记录表（防重 + 统计）
CREATE TABLE seckill_record (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                user_id BIGINT NOT NULL,
                                book_id BIGINT NOT NULL,
                                order_id BIGINT,
                                status TINYINT DEFAULT 0 COMMENT '0-抢购中 1-成功 2-失败',
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE KEY uk_user_book (user_id, book_id),
                                INDEX idx_user_id (user_id)
);
-- 订单支付表
-- 支付单表
CREATE TABLE payment_order (
                               id BIGINT PRIMARY KEY AUTO_INCREMENT,
                               payment_id VARCHAR(64) UNIQUE NOT NULL COMMENT '支付单号（业务主键）',
                               order_id BIGINT NOT NULL COMMENT '订单ID',
                               user_id BIGINT NOT NULL COMMENT '用户ID',
                               amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
                               payment_method VARCHAR(20) NOT NULL COMMENT '支付方式：WECHAT/ALIPAY/MOCK',
                               status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/SUCCESS/FAILED/REFUND',
                               third_party_trade_no VARCHAR(64) COMMENT '第三方交易号',
                               callback_time DATETIME COMMENT '回调时间',
                               expired_at DATETIME COMMENT '支付超时时间（15分钟）',
                               version INT DEFAULT 1 COMMENT '乐观锁',
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                               updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               INDEX idx_order_id (order_id),
                               INDEX idx_user_id (user_id),
                               INDEX idx_payment_id (payment_id)
);
ALTER TABLE payment_order MODIFY order_id BIGINT NOT NULL;
ALTER TABLE payment_order ADD UNIQUE INDEX uk_order_id (order_id);
ALTER TABLE payment_order MODIFY amount BIGINT NULL;
ALTER TABLE payment_order MODIFY COLUMN payment_method VARCHAR(20) NOT NULL COMMENT '支付方式：ALIPAY/WECHAT/UNIONPAY';
ALTER TABLE payment_order ADD COLUMN retry_count INT DEFAULT 0 COMMENT '补偿重试次数';
ALTER TABLE payment_order ADD COLUMN prepay_id VARCHAR(64) COMMENT '第三方预支付ID（仅WAITING状态有效）';
ALTER TABLE payment_order ADD COLUMN ext_info JSON COMMENT '支付渠道扩展信息（JSON）';
ALTER TABLE payment_order MODIFY amount DECIMAL(10,2) NOT NULL COMMENT '支付金额（元），保留两位小数';
ALTER TABLE payment_order ADD COLUMN refund_time DATETIME COMMENT '退款成功时间（最后退款时间）';
ALTER TABLE payment_order ADD COLUMN refunded_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '已退款累计金额（元）';
-- ==========================================
-- 退款记录表
-- ==========================================
CREATE TABLE payment_refund_record (
                                       id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                       payment_id VARCHAR(64) NOT NULL COMMENT '支付单号（关联 payment_order.payment_id）',
                                       out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号（冗余，方便查询）',
                                       trade_no VARCHAR(64) COMMENT '支付宝交易号（冗余，方便查询）',

                                       refund_amount DECIMAL(10,2) NOT NULL COMMENT '本次退款金额（元）',
                                       refund_reason VARCHAR(256) COMMENT '退款原因',
                                       out_request_no VARCHAR(64) NOT NULL COMMENT '退款请求号（幂等键）',

                                       status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '退款状态: PROCESSING/SUCCESS/FAILED',
                                       fail_reason VARCHAR(500) COMMENT '失败原因',

                                       third_party_refund_no VARCHAR(64) COMMENT '第三方退款交易号',
                                       fund_detail JSON COMMENT '资金渠道明细（JSON）',

                                       retry_count INT DEFAULT 0 COMMENT '查询重试次数',
                                       next_query_time DATETIME COMMENT '下次查询时间（PROCESSING时使用）',

                                       operator_id BIGINT COMMENT '操作人ID（后台退款）',
                                       client_ip VARCHAR(45) COMMENT '客户端IP',

                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                       INDEX idx_payment_id (payment_id),
                                       UNIQUE KEY uk_out_request_no (out_request_no),
                                       INDEX idx_status_next_query (status, next_query_time),
                                       INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';
ALTER TABLE `payment_refund_record`
    MODIFY `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
-- ==========================================
-- 支付审计日志表
-- ==========================================
CREATE TABLE payment_audit_log (
                                   id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   trace_id VARCHAR(64) COMMENT '链路追踪ID',
                                   user_id BIGINT COMMENT '操作用户ID',
                                   username VARCHAR(40) COMMENT '操作用户名',
                                   client_ip VARCHAR(45) COMMENT '客户端IP',
                                   user_agent VARCHAR(512) COMMENT '用户代理',

    -- 业务信息
                                   payment_id VARCHAR(64) COMMENT '支付单号',
                                   order_id BIGINT COMMENT '订单ID',
                                   refund_record_id BIGINT COMMENT '退款记录ID',

    -- 操作信息
                                   operation VARCHAR(50) NOT NULL COMMENT '操作类型: CREATE_PAYMENT/PAYMENT_CALLBACK/REFUND/REFUND_CALLBACK/QUERY/CLOSE',
                                   operation_desc VARCHAR(200) COMMENT '操作描述',
                                   request_params TEXT COMMENT '请求参数（脱敏后）',
                                   request_body TEXT COMMENT '请求体（脱敏后）',
                                   response_body TEXT COMMENT '响应体（脱敏后）',

    -- 状态变化
                                   before_status VARCHAR(30) COMMENT '操作前状态',
                                   after_status VARCHAR(30) COMMENT '操作后状态',

    -- 结果
                                   result VARCHAR(10) NOT NULL COMMENT '操作结果: SUCCESS/FAIL/PROCESSING',
                                   error_code VARCHAR(20) COMMENT '错误码',
                                   error_msg VARCHAR(500) COMMENT '错误信息',

    -- 时间
                                   cost_ms BIGINT COMMENT '耗时（毫秒）',
                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                                   INDEX idx_payment_id (payment_id),
                                   INDEX idx_order_id (order_id),
                                   INDEX idx_operation (operation),
                                   INDEX idx_created_at (created_at),
                                   INDEX idx_user_id (user_id),
                                   INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付审计日志表';
ALTER TABLE payment_audit_log
    ADD COLUMN operator_type VARCHAR(20) DEFAULT 'USER' COMMENT '操作者类型: USER/SYSTEM/BATCH';
-- 增加操作者类型字段
ALTER TABLE payment_audit_log MODIFY operator_type VARCHAR(20) DEFAULT 'USER'
        COMMENT '操作者类型: USER/SYSTEM/BATCH/COMPENSATE';
ALTER TABLE payment_audit_log ADD COLUMN prev_hash VARCHAR(64) COMMENT '上一条记录的哈希值';
ALTER TABLE payment_audit_log ADD COLUMN self_hash VARCHAR(64) COMMENT '本记录的哈希值';
-- 更新已有记录的 operator_type（历史数据默认 USER，但可以后续根据业务修正）
UPDATE payment_audit_log SET operator_type = 'USER' WHERE operator_type IS NULL;
-- ==========================================
-- 6. 插入测试数据
-- ==========================================

-- 测试用户（密码：123456）
INSERT INTO `user` (`username`, `email`, `password`, `bio`, `address`) VALUES
                                                                           ('testuser', 'test@test.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', '测试用户', '北京市朝阳区xxx街道'),

                                                                           ('alice', 'alice@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', '前端开发工程师', '上海市浦东新区'),

                                                                           ('bob', 'bob@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', '后端开发工程师', '深圳市南山区');

-- 分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
                                                               ('计算机', 0, 1),
                                                               ('编程语言', 1, 1),
                                                               ('Java', 2, 1),
                                                               ('Python', 2, 2),
                                                               ('前端开发', 1, 2),
                                                               ('JavaScript', 5, 1),
                                                               ('Vue.js', 5, 2);

-- 图书
INSERT INTO `book` (`isbn`, `name`, `author`, `publisher`, `price`, `stock`, `category_id`, `description`) VALUES
                                                                                                               ('978-7-111-12345-6', 'Java核心技术 卷I', 'Cay S. Horstmann', '机械工业出版社', 99.00, 100, 3, 'Java经典入门书籍，涵盖Java基础语法和核心API'),
                                                                                                               ('978-7-111-23456-3', 'Spring Boot实战', 'Craig Walls', '机械工业出版社', 89.00, 50, 3, 'Spring Boot框架实战指南'),
                                                                                                               ('978-7-111-34567-0', '深入理解Java虚拟机', '周志明', '机械工业出版社', 129.00, 30, 3, 'Java虚拟机权威指南'),
                                                                                                               ('978-7-121-45678-9', 'Python核心编程', 'Wesley Chun', '电子工业出版社', 79.00, 60, 4, 'Python编程权威教程'),
                                                                                                               ('978-7-121-56789-0', 'JavaScript高级程序设计', 'Nicholas C. Zakas', '电子工业出版社', 89.00, 40, 6, 'JavaScript经典书籍'),
                                                                                                               ('978-7-302-67890-1', 'Vue.js权威指南', '尤雨溪', '清华大学出版社', 69.00, 35, 7, 'Vue.js框架权威指南');
-- 快速生成 50 个测试用户（用户名 testuser1 ~ testuser50）
INSERT INTO user (username, email, password, address)
SELECT
    CONCAT('testuser', n),
    CONCAT('testuser', n, '@test.com'),
    '$2a$10$FGXNafQ.DtAvNGwojnLDgO8FidPIPd3l6.BlhSLZKfqk1j5Zo8gsK',  -- 密码都是 "123456"
    '北京市朝阳区测试路'
FROM (
         SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
         SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION
         SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION
         SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION
         SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION
         SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30 UNION
         SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION
         SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39 UNION SELECT 40 UNION
         SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44 UNION SELECT 45 UNION
         SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49 UNION SELECT 50
     ) t;