package com.exchange.common.redis.cache.register.version;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.component.codec.VersionCodec;
import com.exchange.common.redis.cache.component.support.VersionedRedisSupport;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class VersionCacheRegister {
    @Bean
    public VersionedRedisSupport versionCacheWriteSupport(
            BaseRedisSupport<String> baseRedisSupport,
            VersionCodec versionCodec, @Qualifier("normalRedissonClient") RedissonClient redissonClient,
            ResourceLoader resourceLoader) {
        return new VersionedRedisSupport(baseRedisSupport, versionCodec, resourceLoader, redissonClient);
    }

    @Bean
    public VersionCacheClient versionJsonRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            VersionedRedisSupport versionCacheWriteSupport) {
        return new VersionCacheClient(baseRedisSupport, versionCacheWriteSupport);
    }
}
