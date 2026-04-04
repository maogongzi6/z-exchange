package com.exchange.common.redis.cache.register.simple;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.factory.ReadableCacheFactory;
import com.exchange.common.redis.cache.component.support.SimpleCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleCacheRegister {
    @Bean
    public SimpleCache simpleCacheSupport(
            BaseRedisSupport<String> baseRedisSupport,
            ValueCodec codec,
            ReadableCacheFactory factory) {
        return new SimpleCache(codec, baseRedisSupport, factory);
    }
}

