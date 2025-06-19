-- KEYS[1]: lockKey
-- ARGV[1]: requestId
-- ARGV[2]: expireTime (seconds)

if redis.call("get", KEYS[1]) == ARGV[1] then
    -- 如果锁仍然由当前请求持有，则重置其过期时间
    return redis.call("expire", KEYS[1], ARGV[2])
else
    return 0
end