package com.exchange.common.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.client.codec.Codec;

@RequiredArgsConstructor
public class BaseClientSideCacheSupport<T> implements BaseCacheReadSupport<T> {
    private final RClientSideCaching clientSideCaching;
    private final Codec codec;

    public T get(String key) {
        RBucket<T> bucket = clientSideCaching.getBucket(key, codec);
        return bucket.get();
    }
}
