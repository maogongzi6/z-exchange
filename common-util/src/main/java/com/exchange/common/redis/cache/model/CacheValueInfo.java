package com.exchange.common.redis.cache.model;

import com.exchange.common.redis.cache.constant.CacheType;
import lombok.RequiredArgsConstructor;

// TODO add a cache specific Result type CacheResult
@RequiredArgsConstructor
public class CacheValueInfo<T> {
    public final T value;
    public final CacheType cacheType;
    public final long version;

    public CacheValueInfo(T value, CacheType cacheType) {
        this.value = value;
        this.cacheType = cacheType;
        this.version = 0;
    }

    public static <T> T getValidValue(CacheValueInfo<T> info) {
        if (info == null || info.value == null) {
            return null;
        }
        if (info.cacheType == CacheType.TOMBSTONE || info.cacheType == CacheType.NEGATIVE) {
            return null;
        }
        return info.value;
    }

    public static boolean isNegative(CacheValueInfo<?> info) {
        return info != null && info.cacheType == CacheType.NEGATIVE;
    }

    // return true if hit a valid cache or a negative cache
    // return false if info is null or hit a tombstone
    public static boolean ifCacheHit(CacheValueInfo<?> info) {
        if (info == null || info.cacheType == CacheType.TOMBSTONE) {
            return false;
        }
        return info.cacheType == CacheType.NEGATIVE || info.value != null;
    }
}
