//package com.exchange.app.ledger.dao.cache;
//
//import com.exchange.app.ledger.constant.cache.CacheScope;
//import com.exchange.common.redis.cache.client.SimpleCacheClient;
//import com.exchange.common.redis.cache.model.CacheValueInfo;
//import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
//import com.exchange.common.redis.cache.strategy.impl.DefaultStableCacheStrategy;
//import com.exchange.common.redis.cache.strategy.ops.NegativeCacheOps;
//import com.exchange.common.redis.cache.strategy.ops.WriteCacheOps;
//import com.exchange.common.utils.TtlStrategy;
//import com.exchange.common.utils.result.Result;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//public class LedgerRefCache implements WriteCacheOps<String>, NegativeCacheOps<String> {
//    private final DefaultStableCacheStrategy<String> stableCacheOps;
//
//    public LedgerRefCache(SimpleCacheClient cacheRedisClient) {
//        stableCacheOps = new DefaultStableCacheStrategy<>(cacheRedisClient, new CacheDescriptor<>(String.class, CacheScope::ledgerRefIdKey));
//    }
//
//
//    @Override
//    public Result<Void> setNegative(String id, TtlStrategy ttl) {
//        return stableCacheOps.setNegative(id, ttl);
//    }
//
//    @Override
//    public Result<Boolean> cleanNegative(String id) {
//        return stableCacheOps.cleanNegative(id);
//    }
//
//    @Override
//    public Result<Void> set(String id, String value, TtlStrategy ttl) {
//        return stableCacheOps.set(id, value, ttl);
//    }
//
//    @Override
//    public Result<CacheValueInfo<String>> get(String id) {
//        return stableCacheOps.get(id);
//    }
//}
