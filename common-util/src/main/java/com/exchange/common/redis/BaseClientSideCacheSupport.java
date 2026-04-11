package com.exchange.common.redis;

import com.exchange.common.redis.aop.CacheExceptionTranslate;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.client.codec.Codec;

@RequiredArgsConstructor
@CacheExceptionTranslate
public class BaseClientSideCacheSupport<T> implements BaseCacheReadSupport<T> {
    private final RClientSideCaching clientSideCaching;
    private final Codec codec;

    public T get(String key) {
        RBucket<T> bucket = clientSideCaching.getBucket(key, codec);
        return bucket.get();
    }
}
