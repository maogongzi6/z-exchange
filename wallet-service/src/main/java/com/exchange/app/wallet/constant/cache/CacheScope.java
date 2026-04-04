package com.exchange.app.wallet.constant.cache;

public class CacheScope {
    final static public String BALANCE_SNAPSHOT_CACHE = "balance_snapshot";

    final static public String BALANCE_SNAPSHOT_REF_ID_SCOPE = BALANCE_SNAPSHOT_CACHE + ":ref";
    final static public String BALANCE_SNAPSHOT_ID_SCOPE = BALANCE_SNAPSHOT_CACHE + ":id";

    public static String balanceSnapshotRefIdKey(String refId) {
        return BALANCE_SNAPSHOT_REF_ID_SCOPE + ":" + refId;
    }

    public static String balanceSnapshotIdKey(String id) {
        return BALANCE_SNAPSHOT_ID_SCOPE + ":" + id;
    }
}
