package com.exchange.common.redis.cache.strategy.discriptor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public final class CacheDescriptor<T> {
    @Getter
    private final Class<T> clazz;
    private final Function<String, String> cacheKeyBuilder;

    public String buildCacheKey(String Id) {
        return cacheKeyBuilder.apply(Id);
    }

}
