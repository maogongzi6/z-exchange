package com.exchange.app.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
public class CustomCacheConfig {
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "app.cache.idemp")
    static public class Idemp {
        private Duration pendingTtl;
        private Duration doneTtl;
    }
}
