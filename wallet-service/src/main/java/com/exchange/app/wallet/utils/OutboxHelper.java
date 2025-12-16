package com.exchange.app.wallet.utils;

import java.time.LocalDateTime;

public class OutboxHelper {
    static public LocalDateTime nextAttempt() {
        return LocalDateTime.now().plusSeconds(COMMON_INTERVAL_SECONDS);
    }

    final static public int COMMON_INTERVAL_SECONDS = 60;
}
