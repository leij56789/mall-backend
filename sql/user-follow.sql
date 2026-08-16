-- 1. 插入测试用户
INSERT INTO `user` (`username`, `email`, `password`, `bio`, `image`) VALUES
                                                                         ('alice', 'alice@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Frontend developer, love React', 'https://api.realworld.io/images/alice.jpg'),
                                                                         ('bob', 'bob@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Backend developer, Java expert', 'https://api.realworld.io/images/bob.jpg'),
                                                                         ('charlie', 'charlie@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Full-stack developer', 'https://api.realworld.io/images/charlie.jpg'),
                                                                         ('david', 'david@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'DevOps engineer', 'https://api.realworld.io/images/david.jpg'),
                                                                         ('emma', 'emma@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Data scientist', 'https://api.realworld.io/images/emma.jpg'),
                                                                         ('frank', 'frank@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Mobile developer', 'https://api.realworld.io/images/frank.jpg');

-- 2. 查看用户 id（记下 id 用于下一步）
SELECT id, username FROM user WHERE username IN ('alice', 'bob', 'charlie', 'david', 'emma', 'frank');

-- 3. 插入关注关系（根据你的实际 id 调整）
-- 假设 johnjacob1 的 id 为 1
INSERT INTO `user_follows` (`follower_id`, `followee_id`) VALUES
                                                              (1, 2), (1, 3), (1, 4),  -- johnjacob1 关注 alice, bob, charlie
                                                              (2, 1), (2, 3),          -- alice 关注 johnjacob1, bob
                                                              (3, 4), (3, 5),          -- bob 关注 charlie, david
                                                              (4, 1), (4, 6),          -- charlie 关注 johnjacob1, emma
                                                              (5, 1),                  -- david 关注 johnjacob1
                                                              (6, 2),                  -- emma 关注 alice
                                                              (7, 1);                  -- frank 关注 johnjacob1

-- 4. 验证数据
SELECT
    u1.username AS follower,
    u2.username AS followee
FROM user_follows f
         JOIN user u1 ON f.follower_id = u1.id
         JOIN user u2 ON f.followee_id = u2.id
ORDER BY u1.username;