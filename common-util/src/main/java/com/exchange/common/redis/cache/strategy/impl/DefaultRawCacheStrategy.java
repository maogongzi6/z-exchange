package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.RawCacheWriter;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.model.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.model.RawStrategyConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultRawCacheStrategy<T> implements RawCacheStrategy<T> {
    private final CacheDescriptor<T> descriptor;
    private final DefaultCacheReader<ValueCodec> cacheReader;
    private final RawCacheWriter cacheWriter;
    private final RawStrategyConfig config;

    // package private constructor. expose factory method for external user
    DefaultRawCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<ValueCodec> cacheReader,
            RawCacheWriter cacheWriter,
            RawStrategyConfig config) {
        this.descriptor = descriptor;
        this.cacheReader = cacheReader;
        this.cacheWriter = cacheWriter;
        this.config = config;
    }


    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String key = descriptor.buildCacheKey(id);
        var result = cacheReader.get(key, descriptor.getClazz());
        if (!result.isSuccess() && Result.is(result, CacheErrorCode.PARSE_CACHE_ERROR)) {
            // ignore recover error
            config.selfRecoverFeature().recover(key);
        }
        return result;
    }

    @Override
    public Result<Boolean> afterDbHit(String id, T value) {
        String key = descriptor.buildCacheKey(id);
        Result<Void> result = cacheWriter.set(key, value, config.ttlConfig().cacheTtl());
        return Result.from(Boolean.TRUE, result);
    }

    // if id is from our service and used internally,
    // it means it is low risky to be attacked, and querying non-existed txn is also rare
    // then, do not set negative value (NULL) when cache misses.
    // if not, we need to set a negative value
    @Override
    public Result<Boolean> afterDbMiss(String id) {
        String key = descriptor.buildCacheKey(id);
        Result<Void> result = config.negativeCacheFeature().set(key, config.ttlConfig().negativeTtl());
        return Result.from(Boolean.TRUE, result);
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value) {
        return afterDbHit(id, value);
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value) {
        String key = descriptor.buildCacheKey(id);
        return config.negativeCacheFeature().clear(key);
    }
}
