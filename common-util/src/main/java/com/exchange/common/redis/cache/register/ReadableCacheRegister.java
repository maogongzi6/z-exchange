package com.exchange.common.redis.cache.register;

import com.exchange.common.redis.BaseClientSideCacheSupport;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.support.DefaultReadableCache;
import com.exchange.common.redis.cache.component.support.ReadableCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
public class ReadableCacheRegister {
    @Primary
    @Bean("defaultReadableCache")
    public ReadableCache defaultReadableCache(
            BaseRedisSupport<String> baseRedisSupport,
            ValueCodec codec) {
        return new DefaultReadableCache(codec, baseRedisSupport);
    }

    @Lazy
    @Bean("clientSideReadableCache")
    public ReadableCache clientSideReadableCache(
            BaseClientSideCacheSupport<String> clientSideCacheSupport,
            ValueCodec codec) {
        return new DefaultReadableCache(codec, clientSideCacheSupport);
    }
}
