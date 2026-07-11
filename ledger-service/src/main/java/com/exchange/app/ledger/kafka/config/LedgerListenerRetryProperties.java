package com.exchange.app.ledger.kafka.config;

import com.exchange.app.ledger.cronjob.outbox.constant.OutboxConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@Data
@ConfigurationProperties(prefix = "app.kafka.listener.retry")
public class LedgerListenerRetryProperties {
    private int attempts = 7;
    private int replyFailureAfterAttempts = 5;
    private int dlqAfterAttempts = 7;
    private long baseAttemptIntervalMs = OutboxConstant.DEFAULT_BASE_ATTEMPT_INTERVAL_MS;
    private long backoffDelayMs = TimeUnit.SECONDS.toMillis(1);
    private double backoffMultiplier = 1.0;
    private long backoffMaxDelayMs = TimeUnit.SECONDS.toMillis(25);
    private boolean autoCreateTopics = false;

    public void validate() {
        if (attempts < dlqAfterAttempts) {
            throw new IllegalArgumentException("app.kafka.listener.retry.attempts must be >= dlq-after-attempts");
        }
    }
}
