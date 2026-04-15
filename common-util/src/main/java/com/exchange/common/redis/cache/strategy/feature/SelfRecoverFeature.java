package com.exchange.common.redis.cache.strategy.feature;

public interface SelfRecoverFeature {
    void recover(String key);
    boolean isEnabled();

    static SelfRecoverFeature disable() {
        return new SelfRecoverFeature() {
            @Override
            public void recover(String key) {}

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
    }
}
