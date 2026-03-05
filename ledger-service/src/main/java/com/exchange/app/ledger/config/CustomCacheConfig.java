package com.exchange.app.ledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@Configuration
public class CustomCacheConfig {
    @Data
    @Validated
    @Configuration
    @ConfigurationProperties(prefix = "app.cache.idemp")
    static public class Idemp {
        // this ttl should be long enough to cover business flow, so that status can turn to ACCEPTED before key expiration in happy flow
        @NotNull
        private Duration pendingTtl;
        @NotNull
        private Duration doneTtl;
    }


}
