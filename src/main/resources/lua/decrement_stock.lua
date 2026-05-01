
local stock = redis.call('GET', KEYS[1])
if stock == false then
    return -2
end
local current = tonumber(stock)
if current <= 0 then
    return -1
end
redis.call('DECRBY', KEYS[1], 1)
return current - 1
