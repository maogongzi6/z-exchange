package com.exchange.common.redis.cache.component.support;

import com.exchange.common.utils.TtlStrategy;

public interface NegativeWriter {
    void setNegative(String key, TtlStrategy ttlStrategy);
}
