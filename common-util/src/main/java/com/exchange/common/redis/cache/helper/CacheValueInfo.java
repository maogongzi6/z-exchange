package com.exchange.common.redis.cache.helper;

import com.exchange.common.redis.cache.constant.CacheType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
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
}
