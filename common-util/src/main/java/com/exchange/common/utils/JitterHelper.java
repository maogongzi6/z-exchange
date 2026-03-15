package com.exchange.common.utils;

import java.time.Duration;
import java.util.Random;

public class JitterHelper {
    final private static Random random = new Random();

    public static Duration jitter(Duration duration, int jitterMs) {
        return duration.plusMillis(random.nextInt(jitterMs));
    }
}
