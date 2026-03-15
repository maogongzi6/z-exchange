//package com.exchange.common.cache.client;
//
//import com.exchange.common.cache.constant.CacheConstant;
//import com.exchange.common.utils.result.Result;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//import java.time.Duration;
//
//@Slf4j
//@RequiredArgsConstructor
//public class VersionedTombstoneCacheRedisClient {
//    VersionedCacheRedisClient versionedCacheRedisClient;
//
//    public Result<Boolean> setTombstone(String key, long newVersion, Duration ttl) {
//        String value = String.format("%d|%s", newVersion, CacheConstant.Tombstone);
//        return versionedCacheRedisClient.setIfAbsentOrNewer(key, value, newVersion, ttl);
//    }
//}
