-- KEYS[1]: product_stock_key (e.g., product:stock:P001)
-- ARGV[1]: quantity to purchase

local current_stock = redis.call("get", KEYS[1])
local quantity = tonumber(ARGV[1])

if current_stock == nil then
    -- 如果商品库存 key 不存在
    return -2 -- 表示 key 不存在
end

current_stock = tonumber(current_stock)

if current_stock >= quantity then
    -- 库存足够，执行原子扣减
    return redis.call("decrby", KEYS[1], quantity)
else
    -- 库存不足
    return -1
end