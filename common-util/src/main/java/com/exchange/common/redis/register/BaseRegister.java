package com.exchange.common.redis.register;

import com.exchange.common.redis.ClientSideCacheReadSupport;
import com.exchange.common.redis.BaseClientSideCacheSupport;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.RedisValueSupport;
import com.exchange.common.redis.metrics.MeteredClientSideCacheReadSupport;
import com.exchange.common.redis.metrics.MeteredRedisValueSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RClientSideCaching;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class BaseRegister {
    @Bean
    public BaseRedisSupport<String> baseRedisSupport(RedisTemplate<String, String> redisTemplate) {
        return new BaseRedisSupport<>(redisTemplate);
    }

    @Primary
    @Bean
    public RedisValueSupport<String> redisValueSupport(
            BaseRedisSupport<String> baseRedisSupport,
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:unknown}") String serviceName) {
        return new MeteredRedisValueSupport<>(baseRedisSupport, meterRegistry, serviceName);
    }

    @Lazy
    @Bean
    BaseClientSideCacheSupport<String> baseAppSideCacheSupport(RClientSideCaching clientSideCaching) {
        return new BaseClientSideCacheSupport<>(clientSideCaching, StringCodec.INSTANCE);
    }

    @Lazy
    @Primary
    @Bean
    ClientSideCacheReadSupport<String> clientSideCacheReadSupport(
            BaseClientSideCacheSupport<String> baseClientSideCacheSupport,
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:unknown}") String serviceName) {
        return new MeteredClientSideCacheReadSupport<>(baseClientSideCacheSupport, meterRegistry, serviceName);
    }
}
