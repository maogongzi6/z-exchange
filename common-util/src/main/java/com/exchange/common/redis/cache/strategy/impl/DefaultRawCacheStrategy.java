package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.RawCacheWriter;
import com.exchange.common.redis.cache.model.CacheReadResult;
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
    public Result<CacheReadResult<T>> get(String id) {
        String key = descriptor.buildCacheKey(id);
        try {
            return Result.success(cacheReader.get(key, descriptor.getClazz()));
        } catch (CacheException e) {
            if (isSelfRecoverableReadError(e)) {
                config.selfRecoverFeature().recover(key);
            }
            throw e;
        }
    }

    @Override
    public Result<Boolean> afterDbHit(String id, T value) {
        String key = descriptor.buildCacheKey(id);
        cacheWriter.set(key, value, config.ttlConfig().cacheTtl());
        return Result.success(Boolean.TRUE);
    }

    // if id is from our service and used internally,
    // it means it is low risky to be attacked, and querying non-existed txn is also rare
    // then, do not set negative value (NULL) when cache misses.
    // if not, we need to set a negative value
    @Override
    public Result<Boolean> afterDbMiss(String id) {
        String key = descriptor.buildCacheKey(id);
        config.negativeCacheFeature().set(key, config.ttlConfig().negativeTtl());
        return Result.success(Boolean.TRUE);
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value) {
        return afterDbHit(id, value);
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value) {
        String key = descriptor.buildCacheKey(id);
        return switch (config.strategyType()) {
            case CACHE_ASIDE -> config.negativeCacheFeature().clear(key);
            case POST_REFRESH -> afterDbHit(id, value);
        };
    }

    private boolean isSelfRecoverableReadError(CacheException e) {
        return e.getErrorCode() == CacheErrorCode.MALFORMED_VALUE
                || e.getErrorCode() == CacheErrorCode.SERIALIZATION;
    }
}
