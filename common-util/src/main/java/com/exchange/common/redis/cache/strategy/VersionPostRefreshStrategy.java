package com.exchange.common.redis.cache.strategy;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

// refresh cache after the db transaction commit
public interface VersionPostRefreshStrategy<T> extends BaseReadStrategy<T> {
    Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl);
}
