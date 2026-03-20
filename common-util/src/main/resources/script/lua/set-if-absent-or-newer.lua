local key = KEYS[1]

local value = ARGV[1]
local newVersion = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

-- get existing value
local oldValue = redis.call('GET', key)

-- if key does not exist, set directly
if not oldValue then
    redis.call('SET', key, value, 'PX', ttl)
    return true
end

-- extract version from existing value: "V|JSON"
local sep = string.find(oldValue, "|")
if not sep then
    -- malformed value, overwrite defensively
    redis.call('SET', key, value, 'PX', ttl)
    return true
end

local oldVersion = tonumber(string.sub(oldValue, 1, sep - 1))

-- if new version >= old version, update
if newVersion >= oldVersion then
    redis.call('SET', key, value, 'PX', ttl)
    return true
end

-- otherwise keep old value
return false