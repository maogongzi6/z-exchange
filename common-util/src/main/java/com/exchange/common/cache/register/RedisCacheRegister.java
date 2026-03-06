package com.exchange.common.cache.register;

import com.exchange.common.cache.client.BaseRedisSupport;
import com.exchange.common.cache.client.CacheRedisClient;
import com.exchange.common.cache.client.JsonVersionedCacheRedisClient;
import com.exchange.common.cache.client.VersionedCacheRedisClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisCacheRegister {
    @Bean
    public BaseRedisSupport<String> baseRedisSupport(@Autowired RedisTemplate<String, String> redisTemplate) {
        return new BaseRedisSupport<>(redisTemplate);
    }

    @Bean
    public CacheRedisClient<String> cacheRedisClient(@Autowired BaseRedisSupport<String> baseRedisSupport) {
        return new CacheRedisClient<>(baseRedisSupport);
    }

    @Bean
    public VersionedCacheRedisClient cacheVersionRedisClient(
            @Autowired BaseRedisSupport<String> baseRedisSupport,
            @Autowired RedissonClient redissonClient,
            @Autowired ResourceLoader resourceLoader) {
        return new VersionedCacheRedisClient(baseRedisSupport, redissonClient, resourceLoader);
    }

    @Bean
    public JsonVersionedCacheRedisClient jsonVersionedCacheRedisClient(
            @Autowired VersionedCacheRedisClient versionedCacheRedisClient,
            @Autowired ObjectMapper objectMapper
    ) {
        return new JsonVersionedCacheRedisClient(versionedCacheRedisClient, objectMapper);
    }
}

