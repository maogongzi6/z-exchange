package com.exchange.common.redis.cache.util;

import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.utils.ValidateHelper;

public class CacheContentValidator {
    public static <T> boolean validateContent(T content, CacheType cacheType) {
        return switch (cacheType) {
            case TOMBSTONE, NEGATIVE -> ValidateHelper.isEmpty(content);
            case STRING, JSON -> !ValidateHelper.isEmpty(content);
        };
    }
}
