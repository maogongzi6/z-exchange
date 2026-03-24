package com.exchange.common.redis.register;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.client.*;
import com.exchange.common.redis.cache.component.codec.ValueCodec;
import com.exchange.common.redis.cache.component.codec.VersionCodec;
import com.exchange.common.redis.cache.component.support.SimpleRedisSupport;
import com.exchange.common.redis.cache.component.support.VersionedRedisSupport;
import org.redisson.api.RedissonClient;
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
    public SimpleRedisSupport simpleCacheSupport(BaseRedisSupport<String> baseRedisSupport, ValueCodec codec) {
        return new SimpleRedisSupport(codec, baseRedisSupport);
    }

    @Bean
    public SimpleCacheClient cacheRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            SimpleRedisSupport simpleCacheSupport) {
        return new SimpleCacheClient(baseRedisSupport, simpleCacheSupport);
    }

    @Bean
    public VersionedRedisSupport versionCacheWriteSupport(BaseRedisSupport<String> baseRedisSupport, VersionCodec versionCodec, RedissonClient redissonClient, ResourceLoader resourceLoader) {
        return new VersionedRedisSupport(baseRedisSupport, versionCodec, resourceLoader, redissonClient);
    }

    @Bean
    public VersionCacheClient versionJsonRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            VersionedRedisSupport versionCacheWriteSupport) {
        return new VersionCacheClient(baseRedisSupport, versionCacheWriteSupport);
    }
}

