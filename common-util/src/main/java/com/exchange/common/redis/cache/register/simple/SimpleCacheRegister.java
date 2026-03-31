package com.exchange.common.redis.cache.register.simple;

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
public class SimpleCacheRegister {
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
}

