-- KEYS[1]: product_stock_key (e.g., product:stock:P001)
-- ARGV[1]: quantity to add

local current_stock = redis.call("get", KEYS[1])
local quantity = tonumber(ARGV[1])

if current_stock == nil then
    -- 如果商品库存 key 不存在，创建并设置初始值
    redis.call("set", KEYS[1], quantity)
    return quantity
end

current_stock = tonumber(current_stock)

-- 执行原子增加
return redis.call("incrby", KEYS[1], quantity) 