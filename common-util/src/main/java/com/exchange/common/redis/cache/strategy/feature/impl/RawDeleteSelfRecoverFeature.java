package com.exchange.common.redis.cache.strategy.feature.impl;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.cache.component.support.RawCacheDeleter;
import com.exchange.common.redis.cache.strategy.feature.SelfRecoverFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawDeleteSelfRecoverFeature implements SelfRecoverFeature {
    private final RawCacheDeleter deleter;

    @Override
    public void recover(String key) {
        try {
            Boolean deleteResult = deleter.delete(key);
            if (!Boolean.TRUE.equals(deleteResult)) {
                log.error("cache delete failed, key: {}", key);
            }
        } catch (CacheException e) {
            log.error("cache delete failed during self recover, key: {}, errorCode: {}", key, e.getErrorCode(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
