-- ============================================
-- RealWorld 数据库初始化脚本
-- ============================================

DROP DATABASE IF EXISTS realworld;
CREATE DATABASE realworld;
USE realworld;

-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE user (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      username VARCHAR(40) NOT NULL UNIQUE,
                      email VARCHAR(60) NOT NULL UNIQUE,
                      password VARCHAR(100) NOT NULL,
                      bio TEXT,
                      image VARCHAR(200),
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      INDEX idx_username (username),
                      INDEX idx_email (email)
);

-- ============================================
-- 2. 文章表
-- ============================================
CREATE TABLE article (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         slug VARCHAR(200) NOT NULL UNIQUE,
                         title VARCHAR(200) NOT NULL,
                         description TEXT NOT NULL,
                         body TEXT NOT NULL,
                         author_id BIGINT NOT NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         FOREIGN KEY (author_id) REFERENCES user(id) ON DELETE CASCADE,
                         INDEX idx_slug (slug)
);

-- ============================================
-- 3. 评论表
-- ============================================
CREATE TABLE comment (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         body TEXT NOT NULL,
                         article_id BIGINT NOT NULL,
                         author_id BIGINT NOT NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE,
                         FOREIGN KEY (author_id) REFERENCES user(id) ON DELETE CASCADE
);

-- ============================================
-- 4. 标签表
-- ============================================
CREATE TABLE tag (
                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
                     name VARCHAR(50) NOT NULL UNIQUE,
                     INDEX idx_name (name)
);

-- ============================================
-- 5. 用户点赞表
-- ============================================
CREATE TABLE user_favorites (
                                user_id BIGINT NOT NULL,
                                article_id BIGINT NOT NULL,
                                PRIMARY KEY (user_id, article_id),
                                FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
                                FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE
);

-- ============================================
-- 6. 用户关注表
-- ============================================
CREATE TABLE user_follows (
                              follower_id BIGINT NOT NULL,
                              followee_id BIGINT NOT NULL,
                              PRIMARY KEY (follower_id, followee_id),
                              FOREIGN KEY (follower_id) REFERENCES user(id) ON DELETE CASCADE,
                              FOREIGN KEY (followee_id) REFERENCES user(id) ON DELETE CASCADE
);

-- ============================================
-- 7. 文章-标签关联表
-- ============================================
CREATE TABLE article_tags (
                              article_id BIGINT NOT NULL,
                              tag_id BIGINT NOT NULL,
                              PRIMARY KEY (article_id, tag_id),
                              FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE,
                              FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

-- ============================================
-- 测试数据
-- ============================================

-- 用户（密码：12345678 的 BCrypt 加密）
INSERT IGNORE INTO user (username, email, password, bio, image) VALUES
                                                                    ('johnjacob1', 'john@example1.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'I love coding!', NULL),
                                                                    ('alice', 'alice@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Frontend developer', NULL),
                                                                    ('bob', 'bob@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Backend developer', NULL);

-- 文章
INSERT IGNORE INTO article (slug, title, description, body, author_id) VALUES
                                                                           ('how-to-train-your-dragon', 'How to train your dragon', 'Ever wonder how?', 'You have to believe', 1),
                                                                           ('my-first-post', 'My First Post', 'Welcome to my blog', 'This is my first article', 1),
                                                                           ('java-tutorial', 'Java Tutorial', 'Learn Java step by step', 'Java is a great language', 1);

-- 标签
INSERT IGNORE INTO tag (name) VALUES
                                  ('dragons'), ('training'), ('java'), ('programming'), ('tutorial'), ('spring'), ('database'), ('docker');

-- 文章-标签关联
INSERT IGNORE INTO article_tags (article_id, tag_id) VALUES
                                                         (1, 1), (1, 2),
                                                         (3, 3), (3, 4), (3, 5);

-- 关注关系
INSERT IGNORE INTO user_follows (follower_id, followee_id) VALUES
                                                               (2, 1), (3, 1);

-- 点赞关系
INSERT IGNORE INTO user_favorites (user_id, article_id) VALUES
                                                            (2, 1), (3, 1);

-- ============================================
-- 验证
-- ============================================
SELECT '=== 数据统计 ===' AS '';
SELECT COUNT(*) AS total_users FROM user;
SELECT COUNT(*) AS total_articles FROM article;
SELECT COUNT(*) AS total_tags FROM tag;
SELECT '=== 初始化完成 ===' AS '';