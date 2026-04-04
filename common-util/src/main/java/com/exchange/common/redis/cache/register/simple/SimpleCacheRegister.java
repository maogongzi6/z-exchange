package com.exchange.common.redis.cache.register.simple;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.factory.CacheReadSupportFactory;
import com.exchange.common.redis.cache.component.support.SimpleRedisSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleCacheRegister {
    @Bean
    public SimpleRedisSupport simpleCacheSupport(
            BaseRedisSupport<String> baseRedisSupport,
            ValueCodec codec,
            CacheReadSupportFactory factory) {
        return new SimpleRedisSupport(codec, baseRedisSupport, factory);
    }
}

