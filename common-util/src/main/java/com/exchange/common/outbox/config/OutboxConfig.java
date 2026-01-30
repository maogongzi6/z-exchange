package com.exchange.common.outbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxConfig {
    private String interval;
    private int attemptIntervalSec = 5;
    private int maxRetries = 5;
    private int pageLimit = 10;
}
