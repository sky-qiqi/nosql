-- 优化版本：添加更多错误处理
local current_stock = redis.call("get", KEYS[1])
local quantity = tonumber(ARGV[1])

if current_stock == false then
    return -2 -- key不存在
end

current_stock = tonumber(current_stock)
if current_stock == nil then
    return -3 -- 数据格式错误
end

if current_stock >= quantity then
    local new_stock = redis.call("decrby", KEYS[1], quantity)
    -- 设置过期时间，防止key永久存在
    redis.call("expire", KEYS[1], 86400) -- 24小时过期
    return new_stock
else
    return -1 -- 库存不足
end