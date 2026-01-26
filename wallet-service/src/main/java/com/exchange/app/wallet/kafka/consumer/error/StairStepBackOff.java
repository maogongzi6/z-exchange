package com.exchange.app.wallet.kafka.consumer.error;

import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.util.Arrays;

public class StairStepBackOff implements BackOff {
    private final long[] delays;
    private final int maxAttempts;
    public StairStepBackOff(long[] delays, int maxAttempts) {
        if (delays == null || delays.length == 0) {
            throw new IllegalArgumentException("invalid delays: " + Arrays.toString(delays));
        }
        for (long delay : delays) {
            if (delay <= 0) {
                throw new IllegalArgumentException("invalid delays: " + Arrays.toString(delays));
            }
        }
        this.delays = delays;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public BackOffExecution start() {
        return new BackOffExecution() {
            private int attempts = 0;

            @Override
            public long nextBackOff() {
                if (attempts >= maxAttempts) {
                    return BackOffExecution.STOP;
                }
                long delay = delays[Math.min(attempts, delays.length - 1)];
                attempts++;
                return delay;
            }
        };
    }
}
