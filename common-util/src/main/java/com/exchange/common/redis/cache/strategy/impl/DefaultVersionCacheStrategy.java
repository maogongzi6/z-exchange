package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.VersionCacheWriter;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.redis.cache.strategy.model.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.model.VersionStrategyConfig;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.redis.cache.strategy.model.StrategyType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultVersionCacheStrategy<T> implements VersionCacheStrategy<T> {
    private final CacheDescriptor<T> descriptor;
    private final DefaultCacheReader<VersionCodec> cacheReader;
    private final VersionCacheWriter cacheWriter;
    private final VersionStrategyConfig config;

    // package private constructor. expose factory method for external user
    public DefaultVersionCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<VersionCodec> cacheReader,
            VersionCacheWriter cacheWriter,
            VersionStrategyConfig config) {
        this.descriptor = descriptor;
        this.cacheReader = cacheReader;
        this.cacheWriter = cacheWriter;
        this.config = config;
    }

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String key = descriptor.buildCacheKey(id);
        Result<CacheValueInfo<T>> result = cacheReader.get(key, descriptor.getClazz());
        if (!result.isSuccess() && Result.is(result, CacheErrorCode.PARSE_CACHE_ERROR)) {
            // ignore recover error
            config.selfRecoverFeature().recover(key);
        }
        return result;
    }

    @Override
    public Result<Boolean> afterDbHit(String id, T value, long newVersion) {
        String key = descriptor.buildCacheKey(id);
        return cacheWriter.setIfAbsentOrNewer(key, value, newVersion, config.ttlConfig().cacheTtl());
    }

    @Override
    public Result<Boolean> afterDbMiss(String id) {
        String key = descriptor.buildCacheKey(id);
        Result<Void> result = config.negativeCacheFeature().set(key, config.ttlConfig().negativeTtl());
        return Result.from(Boolean.TRUE, result);
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value, long newVersion) {
        if (config.strategyType() == StrategyType.CACHE_ASIDE) {
            String key = descriptor.buildCacheKey(id);
            return cacheWriter.setTombstone(key, newVersion, config.ttlConfig().tombstoneTtl());
        } else {
            return afterDbHit(id, value, newVersion);
        }
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value, long newVersion) {
        if (config.strategyType() == StrategyType.CACHE_ASIDE) {
            String key = descriptor.buildCacheKey(id);
            return config.negativeCacheFeature().clear(key);
        } else {
            return afterDbHit(id, value, newVersion);
        }
    }
}
