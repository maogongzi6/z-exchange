package com.exchange.common.redis.cache.component.support;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.CacheEncoder;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.utils.TtlStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawCacheWriter implements NegativeWriter {
    private final CacheEncoder cacheEncoder;
    private final BaseRedisSupport<String> baseRedisSupport;

    public <T> void set(String key, T value, TtlStrategy ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            throw new IllegalArgumentException("set value must not be null");
        }

        CacheType cacheType = CacheType.fromSource(value);
        String encoded = cacheEncoder.encode(value, cacheType);
        baseRedisSupport.set(key, encoded, ttl.afterJitter());
    }

    // conflict risk without version check
    public void setNegative(String key, TtlStrategy ttl) {
        String encoded = cacheEncoder.encode("", CacheType.NEGATIVE);
        baseRedisSupport.set(key, encoded, ttl.afterJitter());
    }
}
