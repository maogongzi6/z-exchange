package com.exchange.common.redis.cache.register;

import com.exchange.common.redis.BaseClientSideCacheSupport;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.*;
import com.exchange.common.redis.component.support.ScriptExecutor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class CacheSupportRegister {
    @Primary
    @Bean("defaultRawCacheReader")
    public DefaultCacheReader<ValueCodec> defaultRawCacheReader(
            ValueCodec codec,
            BaseRedisSupport<String> baseRedisSupport) {
        return new DefaultCacheReader<>(codec, baseRedisSupport);
    }

    @Primary
    @Bean("clientSideRawCacheReader")
    public DefaultCacheReader<ValueCodec> clientSideRawCacheReader(
            ValueCodec codec,
            BaseClientSideCacheSupport<String> baseClientSideCacheSupport) {
        return new DefaultCacheReader<>(codec, baseClientSideCacheSupport);
    }

    @Bean
    public RawCacheWriter rawCacheWriter(
            ValueCodec codec,
            BaseRedisSupport<String> baseRedisSupport) {
        return new RawCacheWriter(codec, baseRedisSupport);
    }

    @Bean
    public RawCacheDeleter rawCacheDeleter(BaseRedisSupport<String> baseRedisSupport) {
        return new RawCacheDeleter(baseRedisSupport);
    }

    @Bean
    public ScriptExecutor scriptExecutor(ResourceLoader resourceLoader) {
        return new ScriptExecutor(resourceLoader);
    }

    @Primary
    @Bean("defaultVersionCacheReader")
    public DefaultCacheReader<VersionCodec> defaultVersionCacheReader(
            VersionCodec codec,
            BaseRedisSupport<String> baseRedisSupport) {
        return new DefaultCacheReader<>(codec, baseRedisSupport);
    }

    @Lazy
    @Bean("clientSideVersionCacheReader")
    public DefaultCacheReader<VersionCodec> clientSideVersionCacheReader(
            VersionCodec codec,
            BaseClientSideCacheSupport<String> clientSideCacheSupport) {
        return new DefaultCacheReader<>(codec, clientSideCacheSupport);
    }

    @Bean
    public VersionCacheWriter versionCacheWriter(
            VersionCodec codec,
            @Qualifier("defaultRedissonClient") RedissonClient redissonClient,
            ScriptExecutor scriptExecutor) {
        return new VersionCacheWriter(codec, redissonClient, scriptExecutor);
    }
}
