-- 1. 检查限购
local userBuyCount = redis.call('GET', KEYS[2])
if userBuyCount then
    return -1;   --已限购
end
-- if tonumber(userBuyCount) >= tonumber(ARGV[2]) then
--     return -1  -- 已限购
-- end

-- 2. 检查库存
local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) < tonumber(ARGV[1]) then
    return 0   -- 库存不足
end

-- 3. 扣库存 & 记录限购
redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], 3600)  -- 限购记录过期时间


return 1  -- 成功