package com.exchange.common.redis.cache.strategy.feature;

public interface SelfRecoverFeature {
    void recover(String key);

    static SelfRecoverFeature disable() {
        return key -> {};
    }
}
