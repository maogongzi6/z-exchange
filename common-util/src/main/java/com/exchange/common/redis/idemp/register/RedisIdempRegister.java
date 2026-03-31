package com.exchange.common.redis.idemp.register;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.idemp.IdempRedisClient;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class RedisIdempRegister {
    @Bean
    public IdempRedisClient idempRedisClient(
            BaseRedisSupport<String> baseRedisSupport,
            @Qualifier("normalRedissonClient") RedissonClient redissonClient,
            ResourceLoader resourceLoader
    ) {
        return new IdempRedisClient(baseRedisSupport, redissonClient, resourceLoader);
    }
}
