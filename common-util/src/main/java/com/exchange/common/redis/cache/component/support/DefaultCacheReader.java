package com.exchange.common.redis.cache.component.support;

import com.exchange.common.redis.BaseCacheReadSupport;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.redis.cache.model.CacheReadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultCacheReader<C extends CacheDecoder> {
    private final C cacheDecoder;
    private final BaseCacheReadSupport<String> baseCacheReadSupport;

    public <T> CacheReadResult<T> get(String key, Class<T> clazz) {
        String value = baseCacheReadSupport.get(key);
        if (value == null) {
            return CacheReadResult.miss();
        }
        return cacheDecoder.decode(value, clazz);
    }
}
