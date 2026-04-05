package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.RawCacheWriter;
import com.exchange.common.redis.cache.component.support.VersionCacheWriter;
import com.exchange.common.redis.cache.strategy.model.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.model.RawStrategyConfig;
import com.exchange.common.redis.cache.strategy.model.VersionStrategyConfig;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import org.springframework.stereotype.Component;

@Component
public final class StrategyFactory {
    public <T> RawCacheStrategy<T> buildRawCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<ValueCodec> cacheReader,
            RawCacheWriter cacheWriter,
            RawStrategyConfig config) {
        return new DefaultRawCacheStrategy<>(descriptor, cacheReader, cacheWriter, config);
    }
    
    public <T> VersionCacheStrategy<T> buildVersionCacheStrategy(
            CacheDescriptor<T> descriptor,
            DefaultCacheReader<VersionCodec> cacheReader,
            VersionCacheWriter cacheWriter,
            VersionStrategyConfig config) {
        return new DefaultVersionCacheStrategy<>(descriptor, cacheReader, cacheWriter, config);
    }
}
