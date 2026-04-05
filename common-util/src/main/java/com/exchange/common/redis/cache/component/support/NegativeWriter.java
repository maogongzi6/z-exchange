package com.exchange.common.redis.cache.component.support;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface NegativeWriter {
    Result<Void> setNegative(String key, TtlStrategy ttlStrategy);
}
