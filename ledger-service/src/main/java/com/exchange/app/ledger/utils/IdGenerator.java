package com.exchange.app.ledger.utils;

import java.sql.Date;

public class IdGenerator {
    final static public long START_TIME = Date.valueOf("2025-11-30").getTime();
    private static long seq = 0;

    public static String generateLedgerTxnId() {
        return String.format("%012d%d", timeOffset(), sequence());
    }

    public static String generateLedgerEntryId() {
        return String.format("%012d%d", timeOffset(), sequence());
    }

    public static String generateAccountId() {
        return String.format("%09d%d", timeOffset()/1000, sequence());
    }

    private static long timeOffset() {
        return System.currentTimeMillis() - START_TIME;
    }

    private static long sequence() {
        seq = (++seq)%10;
        return seq;
    }
}
