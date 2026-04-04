package com.exchange.common.redis.cache.register.version;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.factory.ReadableCacheFactory;
import com.exchange.common.redis.cache.component.support.VersionCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class VersionCacheRegister {
    @Bean
    public VersionCache versionCacheWriteSupport(
            BaseRedisSupport<String> baseRedisSupport,
            VersionCodec versionCodec,
            ReadableCacheFactory factory,
            @Qualifier("normalRedissonClient") RedissonClient redissonClient,
            ResourceLoader resourceLoader) {
        return new VersionCache(baseRedisSupport, versionCodec, factory, resourceLoader, redissonClient);
    }
}
