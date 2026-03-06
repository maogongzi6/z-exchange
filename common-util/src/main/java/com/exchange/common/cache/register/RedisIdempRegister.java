package com.exchange.common.cache.register;

import com.exchange.common.cache.client.BaseRedisSupport;
import com.exchange.common.cache.client.IdempRedisClient;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class RedisIdempRegister {
    @Bean
    public IdempRedisClient idempRedisClient(
            @Autowired BaseRedisSupport<String> baseRedisSupport,
            @Autowired RedissonClient redissonClient,
            @Autowired ResourceLoader resourceLoader
    ) {
        return new IdempRedisClient(baseRedisSupport, redissonClient, resourceLoader);
    }
}
