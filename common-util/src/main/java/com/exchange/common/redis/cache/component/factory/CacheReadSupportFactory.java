package com.exchange.common.redis.cache.component.factory;

import com.exchange.common.redis.BaseCacheReadSupport;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.redis.cache.component.support.CacheReadSupport;
import org.springframework.stereotype.Component;

@Component
public class CacheReadSupportFactory {
    public CacheReadSupport create(CacheDecoder codec, BaseCacheReadSupport<String> baseCacheReadSupport) {
        return new CacheReadSupport(codec, baseCacheReadSupport);
    }
}