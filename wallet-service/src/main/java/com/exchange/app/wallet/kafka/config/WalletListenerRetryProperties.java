package com.exchange.app.wallet.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@Data
@ConfigurationProperties(prefix = "app.kafka.listener.retry")
public class WalletListenerRetryProperties {
    private int attempts = 7;
    private long backoffDelayMs = TimeUnit.SECONDS.toMillis(1);
    private double backoffMultiplier = 1.0;
    private long backoffMaxDelayMs = TimeUnit.SECONDS.toMillis(25);
    private boolean autoCreateTopics = false;

    public void validate() {
        if (attempts < 1) {
            throw new IllegalArgumentException("app.kafka.listener.retry.attempts must be >= 1");
        }
    }
}
