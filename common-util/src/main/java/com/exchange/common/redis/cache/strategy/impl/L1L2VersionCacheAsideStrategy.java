//package com.exchange.common.redis.cache.strategy.impl;
//
//import com.exchange.common.redis.cache.model.CacheValueInfo;
//import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
//import com.exchange.common.utils.TtlStrategy;
//import com.exchange.common.utils.result.Result;
//
//public class L1L2VersionCacheAsideStrategy<T> implements VersionCacheAsideStrategy<T> {
//    @Override
//    public Result<CacheValueInfo<T>> get(String id) {
//        return null;
//    }
//
//    @Override
//    public Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl) {
//        return null;
//    }
//
//    @Override
//    public Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl) {
//        return null;
//    }
//
//    @Override
//    public Result<Boolean> setNegative(String id, long version, TtlStrategy ttl) {
//        return null;
//    }
//
//    @Override
//    public Result<Boolean> cleanNegative(String id) {
//        return null;
//    }
//}
