package com.exchange.app.wallet.utils;

import java.sql.Date;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    final static public long START_TIME = Date.valueOf("2025-11-30").getTime();
    private static final AtomicLong seq = new AtomicLong(0);

    public static String generateWalletId() {
        return String.format("%d-%d", timeOffset(), sequence());
    }

    public static String generateWalletTransactionId() {
        return String.format("%d-%d", timeOffset(), sequence());
    }

    public static String generateWalletActionId() {
        return String.format("%d-%d", timeOffset(), sequence());
    }

    public static String generateReservationId() {
        return String.format("%d-%d", timeOffset(), sequence());
    }

    public static String generateEventId(String commandId) {
        return String.format("%s-%d", commandId, sequence());
    }

    private static long timeOffset() {
        return System.currentTimeMillis() - START_TIME;
    }

    private static long sequence() {
        return seq.incrementAndGet();
    }
}
