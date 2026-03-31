package com.exchange.common.outbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Data
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {
    @NotBlank
    private String interval;
    private int attemptIntervalSec = 5;
    private int maxRetries = 5;
    private int pageLimit = 10;
}
