package com.exchange.common.redis.cache.register;

import com.exchange.common.redis.ClientSideCacheReadSupport;
import com.exchange.common.redis.RedisValueSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.*;
import com.exchange.common.redis.component.RedisScriptExecutor;
import com.exchange.common.redis.component.ScriptExecutor;
import com.exchange.common.redis.metrics.MeteredRedisScriptExecutor;
import io.micrometer.core.instrument.MeterRegistry;
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
            RedisValueSupport<String> baseRedisSupport) {
        return new DefaultCacheReader<>(codec, baseRedisSupport);
    }

    @Primary
    @Bean("clientSideRawCacheReader")
    public DefaultCacheReader<ValueCodec> clientSideRawCacheReader(
            ValueCodec codec,
            ClientSideCacheReadSupport<String> baseClientSideCacheSupport) {
        return new DefaultCacheReader<>(codec, baseClientSideCacheSupport);
    }

    @Bean
    public RawCacheWriter rawCacheWriter(
            ValueCodec codec,
            RedisValueSupport<String> baseRedisSupport) {
        return new RawCacheWriter(codec, baseRedisSupport);
    }

    @Bean
    public RawCacheDeleter rawCacheDeleter(RedisValueSupport<String> baseRedisSupport) {
        return new RawCacheDeleter(baseRedisSupport);
    }

    @Bean
    public ScriptExecutor scriptExecutor(ResourceLoader resourceLoader) {
        return new ScriptExecutor(resourceLoader);
    }

    @Primary
    @Bean
    public RedisScriptExecutor redisScriptExecutor(
            ScriptExecutor scriptExecutor,
            MeterRegistry meterRegistry) {
        return new MeteredRedisScriptExecutor(scriptExecutor, meterRegistry);
    }

    @Primary
    @Bean("defaultVersionCacheReader")
    public DefaultCacheReader<VersionCodec> defaultVersionCacheReader(
            VersionCodec codec,
            RedisValueSupport<String> baseRedisSupport) {
        return new DefaultCacheReader<>(codec, baseRedisSupport);
    }

    @Lazy
    @Bean("clientSideVersionCacheReader")
    public DefaultCacheReader<VersionCodec> clientSideVersionCacheReader(
            VersionCodec codec,
            ClientSideCacheReadSupport<String> clientSideCacheSupport) {
        return new DefaultCacheReader<>(codec, clientSideCacheSupport);
    }

    @Bean
    public VersionCacheWriter versionCacheWriter(
            VersionCodec codec,
            RedisValueSupport<String> baseRedisSupport,
            @Qualifier("defaultRedissonClient") RedissonClient redissonClient,
            RedisScriptExecutor scriptExecutor) {
        return new VersionCacheWriter(codec, baseRedisSupport, redissonClient, scriptExecutor);
    }
}
