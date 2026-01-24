package com.exchange.app.ledger.cronjob.constant;

import java.util.concurrent.TimeUnit;

public class OutboxConfig {
    final static public long BASE_ATTEMPT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
}
