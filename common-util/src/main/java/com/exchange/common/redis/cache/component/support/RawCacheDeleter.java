package com.exchange.common.redis.cache.component.support;

import com.exchange.common.redis.RedisValueSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawCacheDeleter {
    private final RedisValueSupport<String> baseRedisSupport;

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
