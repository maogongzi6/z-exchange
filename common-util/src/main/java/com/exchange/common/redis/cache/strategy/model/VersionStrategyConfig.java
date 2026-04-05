package com.exchange.common.redis.cache.strategy.model;

import com.exchange.common.redis.cache.strategy.feature.NegativeCacheFeature;
import com.exchange.common.redis.cache.strategy.feature.SelfRecoverFeature;
import com.exchange.common.utils.TtlStrategy;

public record VersionStrategyConfig(StrategyType strategyType, TtlConfig ttlConfig, SelfRecoverFeature selfRecoverFeature, NegativeCacheFeature negativeCacheFeature) {
    public record TtlConfig(TtlStrategy cacheTtl, TtlStrategy negativeTtl, TtlStrategy tombstoneTtl) {}

    public VersionStrategyConfig(StrategyType strategyType, TtlConfig ttlConfig) {
        this(strategyType, ttlConfig, SelfRecoverFeature.disable(), NegativeCacheFeature.disable());
    }
}
