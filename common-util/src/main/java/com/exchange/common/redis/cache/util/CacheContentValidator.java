package com.exchange.common.redis.cache.util;

import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.utils.ValidateHelper;

public class CacheContentValidator {
    public static <T> boolean validateContent(T content, CacheType cacheType) {
        switch (cacheType) {
            case TOMBSTONE:
            case NEGATIVE:
                return ValidateHelper.isEmpty(content);
            case STRING:
            case JSON:
                return !ValidateHelper.isEmpty(content);
        }
        return false;
    }
}
