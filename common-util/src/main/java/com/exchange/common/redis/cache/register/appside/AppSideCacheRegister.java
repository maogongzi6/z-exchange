package com.exchange.common.redis.cache.register.appside;

import com.exchange.common.redis.BaseAppSideCacheSupport;
import com.exchange.common.redis.cache.client.VersionAppSideCacheReadClient;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.factory.CacheReadSupportFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppSideCacheRegister {
    @Bean
    public VersionAppSideCacheReadClient versionAppSideCacheReadClient(
            VersionCodec codec,
            BaseAppSideCacheSupport<String> baseAppSideCacheSupport,
            CacheReadSupportFactory factory) {
        return new VersionAppSideCacheReadClient(codec, baseAppSideCacheSupport, factory);
    }
}
