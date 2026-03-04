local v = redis.call("GET", KEYS[1])
if not v then
  return ""                 -- key already gone
end

if v == ARGV[1] then         -- ARGV[1] is expected "P:<hash>:<token>"
  redis.call("DEL", KEYS[1])
  return v
end

return ""                    -- not owner or not P state anymore