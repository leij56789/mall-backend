-- 1. 用户表
CREATE TABLE `user` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `username` VARCHAR(40) NOT NULL UNIQUE,
                        `email` VARCHAR(60) NOT NULL UNIQUE,
                        `password` VARCHAR(100) NOT NULL,
                        `bio` TEXT,
                        `image` VARCHAR(200),
                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX `idx_username` (`username`),
                        INDEX `idx_email` (`email`)
);

-- 2. 文章表
CREATE TABLE `article` (
                           `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                           `slug` VARCHAR(200) NOT NULL UNIQUE,
                           `title` VARCHAR(200) NOT NULL,
                           `description` TEXT NOT NULL,
                           `body` TEXT NOT NULL,
                           `author_id` BIGINT NOT NULL,
                           `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           FOREIGN KEY (`author_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
                           INDEX `idx_slug` (`slug`),
                           INDEX `idx_author_id` (`author_id`)
);

-- 3. 评论表
CREATE TABLE `comment` (
                           `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                           `body` TEXT NOT NULL,
                           `article_id` BIGINT NOT NULL,
                           `author_id` BIGINT NOT NULL,
                           `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
                           FOREIGN KEY (`author_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
                           INDEX `idx_article_id` (`article_id`)
);

-- 4. 标签表
CREATE TABLE `tag` (
                       `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                       `name` VARCHAR(50) NOT NULL UNIQUE,
                       INDEX `idx_name` (`name`)
);

-- 5. 用户点赞记录表（多对多）
CREATE TABLE `user_favorites` (
                                  `user_id` BIGINT NOT NULL,
                                  `article_id` BIGINT NOT NULL,
                                  PRIMARY KEY (`user_id`, `article_id`),
                                  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
                                  FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE
);

-- 6. 用户关注记录表（多对多）
CREATE TABLE `user_follows` (
                                `follower_id` BIGINT NOT NULL,
                                `followee_id` BIGINT NOT NULL,
                                PRIMARY KEY (`follower_id`, `followee_id`),
                                FOREIGN KEY (`follower_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
                                FOREIGN KEY (`followee_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

-- 7. 文章标签关联表（多对多）
CREATE TABLE `article_tags` (
                                `article_id` BIGINT NOT NULL,
                                `tag_id` BIGINT NOT NULL,
                                PRIMARY KEY (`article_id`, `tag_id`),
                                FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
                                FOREIGN KEY (`tag_id`) REFERENCES `tag`(`id`) ON DELETE CASCADE
);