package com.exchange.common.redis.idemp.register;

import com.exchange.common.redis.RedisValueSupport;
import com.exchange.common.redis.component.RedisScriptExecutor;
import com.exchange.common.redis.idemp.DefaultIdempotencyClient;
import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.common.redis.idemp.IdempotencyClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisIdempRegister {
    @Bean
    public IdempRedisClient idempRedisClient(
            RedisValueSupport<String> baseRedisSupport,
            @Qualifier("defaultRedissonClient") RedissonClient redissonClient,
            RedisScriptExecutor scriptExecutor
    ) {
        return new IdempRedisClient(baseRedisSupport, redissonClient, scriptExecutor);
    }

    @Bean
    public IdempotencyClient idempotencyClient(
            IdempRedisClient idempRedisClient,
            MeterRegistry meterRegistry
    ) {
        return new DefaultIdempotencyClient(idempRedisClient, meterRegistry);
    }
}
