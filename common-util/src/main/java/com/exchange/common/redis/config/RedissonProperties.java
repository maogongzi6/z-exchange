package com.exchange.common.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "app.redisson")
public class RedissonProperties {
    private ClientProperties normalProperties = new ClientProperties();
    private ClientProperties clientSideCacheProperties = new ClientProperties();

    @Data
    public static class ClientProperties {
        @NotEmpty
        private String clientName;
        @Min(1)
        private int connectionMinimumIdleSize = 8;
        @Min(1)
        private int connectionPoolSize = 32;
        private Duration clientSideLifetime = Duration.ofMillis(100);
        private Duration timeout = Duration.ofSeconds(3);
        private Duration connectionTimeout = Duration.ofSeconds(10);
    }
}
