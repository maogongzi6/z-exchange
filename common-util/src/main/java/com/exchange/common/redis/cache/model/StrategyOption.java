package com.exchange.common.redis.cache.model;

import com.exchange.common.utils.TtlStrategy;

public record StrategyOption(TtlOption ttlOption, boolean requireTombstone, boolean requireNegativeCache) {
    public record TtlOption(TtlStrategy entityTtl, TtlStrategy negativeTtl, TtlStrategy tombstoneTtl) {}
}
