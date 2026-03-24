package com.exchange.common.redis.cache.constant;

import com.exchange.common.utils.enums.EnumMapper;

import java.util.HashMap;

public enum CacheType {
    STRING("S"), JSON("J"), TOMBSTONE("T"), NEGATIVE("N"),
    ;

    final public String marker;

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

    public static final EnumMapper<CacheType, String> mapper = new EnumMapper<>(
            new HashMap<>() {{
                put(CacheType.STRING, "S");
                put(CacheType.JSON, "J");
                put(CacheType.TOMBSTONE, "T");
                put(CacheType.NEGATIVE, "N");
            }}
    );
}
