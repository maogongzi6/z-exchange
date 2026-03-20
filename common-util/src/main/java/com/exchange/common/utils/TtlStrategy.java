package com.exchange.common.utils;

import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.Random;

@AllArgsConstructor
public class TtlStrategy {
    final static Random random = new Random();

    private final Duration ttl;
    private final Duration jitter;

    public Duration afterJitter() {
        long l = jitter.toMillis();
        if (l <= 0) {
            return ttl;
        }

        int i = l > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) l;
        return ttl.plusMillis(random.nextInt(i));
    }
}
