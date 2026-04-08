package com.exchange.common.redis.cache.strategy.feature.impl;

import com.exchange.common.redis.cache.component.support.RawCacheDeleter;
import com.exchange.common.redis.cache.strategy.feature.SelfRecoverFeature;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawDeleteSelfRecoverFeature implements SelfRecoverFeature {
    private final RawCacheDeleter deleter;

    @Override
    public void recover(String key) {
        Result<Boolean> deleteResult = deleter.delete(key);
        if (!deleteResult.isSuccess() || !deleteResult.getValue()) {
            log.error("cache delete failed, key: {}", key);
        }
    }
}
