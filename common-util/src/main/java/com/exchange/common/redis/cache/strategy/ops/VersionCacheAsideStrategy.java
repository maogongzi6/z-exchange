//package com.exchange.common.redis.cache.strategy.base;
//
//import com.exchange.common.utils.TtlStrategy;
//import com.exchange.common.utils.result.Result;
//
//public interface VersionCacheAsideStrategy<T> extends BaseReadOps<T> {
//    Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl);
//    Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl);
//}
