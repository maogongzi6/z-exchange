package com.exchange.common.redis.cache.component.support;

import com.exchange.common.result.Result;
import com.exchange.common.utils.TtlStrategy;

public interface NegativeWriter {
    Result<Void> setNegative(String key, TtlStrategy ttlStrategy);
}
