package com.exchange.app.wallet.cronjob.outbox.constant;

import java.util.concurrent.TimeUnit;

public class OutboxConstant {
    final static public long BASE_ATTEMPT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
}
