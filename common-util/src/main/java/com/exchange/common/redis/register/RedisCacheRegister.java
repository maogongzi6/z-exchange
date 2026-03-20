package com.exchange.common.redis.register;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.client.*;
import com.exchange.common.redis.cache.component.parser.ValueParser;
import com.exchange.common.redis.cache.component.parser.VersionParser;
import com.exchange.common.redis.cache.component.support.SimpleCacheSupport;
import com.exchange.common.redis.cache.component.support.VersionCacheWriteSupport;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisCacheRegister {
    @Bean
    public BaseRedisSupport<String> baseRedisSupport(RedisTemplate<String, String> redisTemplate) {
        return new BaseRedisSupport<>(redisTemplate);
    }

    @Bean
    public SimpleCacheSupport simpleCacheSupport(BaseRedisSupport<String> baseRedisSupport, ValueParser valueParseHelper) {
        return new SimpleCacheSupport(valueParseHelper, baseRedisSupport);
    }

    @Bean
    public StringRedisClient cacheRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            SimpleCacheSupport simpleCacheSupport) {
        return new StringRedisClient(baseRedisSupport, simpleCacheSupport);
    }

    @Bean
    public VersionCacheWriteSupport versionCacheWriteSupport(BaseRedisSupport<String> baseRedisSupport, VersionParser versionHelper, RedissonClient redissonClient, ResourceLoader resourceLoader) {
        return new VersionCacheWriteSupport(baseRedisSupport, versionHelper, resourceLoader, redissonClient);
    }

    @Bean
    public VersionJsonRedisClient versionJsonRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            VersionCacheWriteSupport versionCacheWriteSupport) {
        return new VersionJsonRedisClient(baseRedisSupport, versionCacheWriteSupport);
    }
}

