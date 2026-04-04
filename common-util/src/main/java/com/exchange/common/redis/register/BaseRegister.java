package com.exchange.common.redis.register;

import com.exchange.common.redis.BaseClientSideCacheSupport;
import com.exchange.common.redis.BaseRedisSupport;
import org.redisson.api.RClientSideCaching;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class BaseRegister {
    @Bean
    public BaseRedisSupport<String> baseRedisSupport(RedisTemplate<String, String> redisTemplate) {
        return new BaseRedisSupport<>(redisTemplate);
    }

    @Bean
    @Lazy
    BaseClientSideCacheSupport<String> baseAppSideCacheSupport(RClientSideCaching clientSideCaching) {
        return new BaseClientSideCacheSupport<>(clientSideCaching, StringCodec.INSTANCE);
    }
}
