package com.exchange.app.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
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
        @NotNull
        private Duration pendingTtl;
        @NotNull
        private Duration doneTtl;
        @Min(1)
        @NotNull
        private Integer jitterMs;
    }
}
