package com.exchange.common.redis.cache.constant;

public enum CacheType {
    STRING("S"), JSON("J"), TOMBSTONE("T"), NEGATIVE("N"),
    ;

    final public String marker;

    // placeholder for tombstone and negative value, format like, T:PH, N:PH
    static public final String PLACEHOLDER = "PH";

    CacheType(String marker) {
        this.marker = marker;
    }

    public static <T> CacheType fromSource(T source) {
        if (source instanceof String) {
            return CacheType.STRING;
        } else {
            return CacheType.JSON;
        }
    }
}
