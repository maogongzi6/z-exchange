package com.exchange.common.redis.register;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.client.*;
import com.exchange.common.redis.cache.helper.ValueParseHelper;
import com.exchange.common.redis.cache.helper.VersionHelper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
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
    public StringRedisClient cacheRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            @Qualifier("simpleCacheReadSupport") CacheReadSupport cacheReadSupport,
            CacheWriteSupport cacheWriteSupport
            ) {
        return new StringRedisClient(baseRedisSupport, cacheReadSupport, cacheWriteSupport);
    }

    @Bean
    public VersionCacheWriteSupport versionCacheWriteSupport(VersionHelper versionHelper, RedissonClient redissonClient, ResourceLoader resourceLoader) {
        return new VersionCacheWriteSupport(versionHelper, resourceLoader, redissonClient);
    }

    @Bean(name = "versionCacheReadSupport")
    public CacheReadSupport versionCacheReadSupport(BaseRedisSupport<String> baseRedisSupport, VersionHelper versionHelper) {
        return new CacheReadSupport(versionHelper, baseRedisSupport);
    }

    @Bean
    public CacheWriteSupport simpleCacheWriteSupport(BaseRedisSupport<String> baseRedisSupport, ValueParseHelper valueParseHelper) {
        return new CacheWriteSupport(valueParseHelper, baseRedisSupport);
    }

    @Bean(name = "simpleCacheReadSupport")
    public CacheReadSupport simpleCacheReadSupport(BaseRedisSupport<String> baseRedisSupport, ValueParseHelper valueParseHelper) {
        return new CacheReadSupport(valueParseHelper, baseRedisSupport);
    }


    @Bean
    public VersionJsonRedisClient versionJsonRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            @Qualifier("versionCacheReadSupport") CacheReadSupport cacheReadSupport,
            VersionCacheWriteSupport versionCacheWriteSupport) {
        return new VersionJsonRedisClient(baseRedisSupport, cacheReadSupport, versionCacheWriteSupport);
    }
}

