package com.exchange.stress.ledger;

final class LedgerSmokeConfig {
    static final String HOST = stringValue("LEDGER_GRPC_HOST", "172.31.16.37");
    static final int PORT = positiveIntValue("LEDGER_GRPC_PORT", 9191);
    static final String HTTP_HOST = stringValue("LEDGER_HTTP_HOST", HOST);
    static final int HTTP_PORT = positiveIntValue("LEDGER_HTTP_PORT", 8081);
    static final String ACCOUNT_REF = stringValue("LEDGER_TEST_ACCOUNT_REF", "gatling-smoke-account");
    static final String ASSET_ID = stringValue("LEDGER_TEST_ASSET_ID", "asset-1");
    static final long AMOUNT = positiveLongValue("LEDGER_TEST_AMOUNT", 100);
    static final int DEADLINE_SECONDS = positiveIntValue("LEDGER_GRPC_DEADLINE_SECONDS", 5);

    private LedgerSmokeConfig() {
    }

    private static String stringValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int positiveIntValue(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static long positiveLongValue(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }
}
