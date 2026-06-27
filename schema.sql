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