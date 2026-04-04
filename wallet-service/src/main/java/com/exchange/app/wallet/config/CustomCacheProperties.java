package com.exchange.app.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;

public class CustomCacheProperties {
    @lombok.Data
    @Validated
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

    @lombok.Data
    @Validated
    @ConfigurationProperties(prefix = "app.cache.data")
    static public class Data {
        @NotNull
        private Duration entityCacheTtl;
        @NotNull
        private Duration indexCacheTtl;
        @NotNull
        private Duration negativeTtl;
        @NotNull
        private Duration tombstoneTtl;
        @NotNull
        private Duration jitter;
    }
}
