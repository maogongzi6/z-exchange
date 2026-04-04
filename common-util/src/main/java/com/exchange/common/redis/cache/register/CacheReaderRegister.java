package com.exchange.common.redis.cache.register;

import com.exchange.common.redis.BaseClientSideCacheSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.support.CacheReadSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
public class CacheReaderRegister {
    @Primary
    @Bean("defaultCacheReadSupport")
    public CacheReadSupport defaultCacheReadSupport(
            BaseClientSideCacheSupport<String> appSideCacheSupport,
            ValueCodec codec) {
        return new CacheReadSupport(codec, appSideCacheSupport);
    }

    @Lazy
    @Bean("clientSideCacheReadSupport")
    public CacheReadSupport clientSideCacheReadSupport(
            BaseClientSideCacheSupport<String> clientSideCacheSupport,
            ValueCodec codec) {
        return new CacheReadSupport(codec, clientSideCacheSupport);
    }
}
