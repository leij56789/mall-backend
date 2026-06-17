USE realworld;

-- 插入测试用户（密码统一为 12345678 的 BCrypt 加密）
INSERT INTO `user` (`username`, `email`, `password`, `bio`, `image`) VALUES
                                                                         ('johnjacob1', 'john@example1.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'I love coding!', NULL),
                                                                         ('alice', 'alice@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Frontend developer', NULL),
                                                                         ('bob', 'bob@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Backend developer', NULL);

-- 插入测试文章
INSERT INTO `article` (`slug`, `title`, `description`, `body`, `author_id`) VALUES
                                                                                ('how-to-train-your-dragon', 'How to train your dragon', 'Ever wonder how?', 'You have to believe', 1),
                                                                                ('my-first-post', 'My First Post', 'Welcome to my blog', 'This is my first article', 1),
                                                                                ('java-tutorial', 'Java Tutorial', 'Learn Java step by step', 'Java is a great language', 1);

-- 插入标签
INSERT INTO `tag` (`name`) VALUES
                               ('dragons'), ('training'), ('java'), ('programming'), ('tutorial'), ('spring'), ('database'), ('docker');

-- 插入文章-标签关联
INSERT INTO `article_tags` (`article_id`, `tag_id`) VALUES
                                                        (1, 1), (1, 2),
                                                        (3, 3), (3, 4), (3, 5);

-- 插入关注关系（alice 和 bob 关注 johnjacob1）
INSERT INTO `user_follows` (`follower_id`, `followee_id`) VALUES
                                                              (2, 1), (3, 1);

-- 插入点赞关系（alice 和 bob 点赞文章1）
INSERT INTO `user_favorites` (`user_id`, `article_id`) VALUES
                                                           (2, 1), (3, 1);