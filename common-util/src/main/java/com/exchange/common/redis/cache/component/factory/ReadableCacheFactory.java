package com.exchange.common.redis.cache.component.factory;

import com.exchange.common.redis.BaseCacheReadSupport;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.redis.cache.component.support.DefaultReadableCache;
import org.springframework.stereotype.Component;

@Component
public class ReadableCacheFactory {
    public DefaultReadableCache create(CacheDecoder codec, BaseCacheReadSupport<String> baseCacheReadSupport) {
        return new DefaultReadableCache(codec, baseCacheReadSupport);
    }
}