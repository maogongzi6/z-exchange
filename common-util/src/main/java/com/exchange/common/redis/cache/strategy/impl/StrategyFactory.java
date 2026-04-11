package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.RawCacheWriter;
import com.exchange.common.redis.cache.component.support.VersionCacheWriter;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.model.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.model.RawStrategyConfig;
import com.exchange.common.redis.cache.strategy.model.VersionStrategyConfig;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.result.Result;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public final class StrategyFactory {
    public <T> RawCacheStrategy<T> buildRawCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<ValueCodec> cacheReader,
            RawCacheWriter cacheWriter,
            RawStrategyConfig config) {
        return new DefaultRawCacheStrategy<>(descriptor, cacheReader, cacheWriter, config);
    }

    public <T> RawCacheStrategy<T> buildRawCacheSuppressExceptionStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<ValueCodec> cacheReader,
            RawCacheWriter cacheWriter,
            RawStrategyConfig config) {
        return suppressCacheException(buildRawCacheStrategy(descriptor, cacheReader, cacheWriter, config));
    }

    public <T> VersionCacheStrategy<T> buildVersionCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<VersionCodec> cacheReader,
            VersionCacheWriter cacheWriter,
            VersionStrategyConfig config) {
        return new DefaultVersionCacheStrategy<>(descriptor, cacheReader, cacheWriter, config);
    }

    public <T> VersionCacheStrategy<T> buildVersionCacheSuppressExceptionStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<VersionCodec> cacheReader,
            VersionCacheWriter cacheWriter,
            VersionStrategyConfig config) {
        return suppressCacheException(buildVersionCacheStrategy(descriptor, cacheReader, cacheWriter, config));
    }

    private static <T> RawCacheStrategy<T> suppressCacheException(RawCacheStrategy<T> delegate) {
        return new RawCacheStrategy<>() {
            @Override
            public Result<CacheValueInfo<T>> get(String id) {
                return downgrade(() -> delegate.get(id));
            }

            @Override
            public Result<Boolean> afterDbHit(String id, T value) {
                return downgrade(() -> delegate.afterDbHit(id, value));
            }

            @Override
            public Result<Boolean> afterDbMiss(String id) {
                return downgrade(() -> delegate.afterDbMiss(id));
            }

            @Override
            public Result<Boolean> afterUpdate(String id, T value) {
                return downgrade(() -> delegate.afterUpdate(id, value));
            }

            @Override
            public Result<Boolean> afterInsert(String id, T value) {
                return downgrade(() -> delegate.afterInsert(id, value));
            }
        };
    }

    private static <T> VersionCacheStrategy<T> suppressCacheException(VersionCacheStrategy<T> delegate) {
        return new VersionCacheStrategy<>() {
            @Override
            public Result<CacheValueInfo<T>> get(String id) {
                return downgrade(() -> delegate.get(id));
            }

            @Override
            public Result<Boolean> afterDbHit(String id, T value, long newVersion) {
                return downgrade(() -> delegate.afterDbHit(id, value, newVersion));
            }

            @Override
            public Result<Boolean> afterDbMiss(String id) {
                return downgrade(() -> delegate.afterDbMiss(id));
            }

            @Override
            public Result<Boolean> afterUpdate(String id, T value, long newVersion) {
                return downgrade(() -> delegate.afterUpdate(id, value, newVersion));
            }

            @Override
            public Result<Boolean> afterInsert(String id, T value, long newVersion) {
                return downgrade(() -> delegate.afterInsert(id, value, newVersion));
            }
        };
    }

    private static <T> Result<T> downgrade(Supplier<Result<T>> invocation) {
        try {
            return invocation.get();
        } catch (CacheException e) {
            return Result.failure(e.getErrorCode(), e.getMessage());
        }
    }
}
