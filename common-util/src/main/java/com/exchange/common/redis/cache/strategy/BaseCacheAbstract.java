//package com.exchange.common.redis.cache.strategy;
//
//import com.exchange.common.redis.cache.client.CacheReader;
//import com.exchange.common.redis.cache.model.CacheValueInfo;
//import com.exchange.common.redis.cache.strategy.impl.BaseReadStrategy;
//import com.exchange.common.utils.result.CommonErrorCode;
//import com.exchange.common.utils.result.Result;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@AllArgsConstructor
//public abstract class BaseCacheAbstract<T> implements BaseReadStrategy<T> {
//    private final CacheReader cacheReader;
//
//    @Override
//    public Result<CacheValueInfo<T>> get(String id) {
//        String cacheKey = getCacheKey(id);
//        // check cache
//        // if id is from our service and used internally,
//        // it means it is low risky to be attacked, and querying non-existed txn is also rare
//        // then, do not set negative value (NULL) when cache misses.
//        // if not, we need to set a negative value
//        Result<CacheValueInfo<T>> cacheResult = cacheReader.get(cacheKey, getClazz());
//        if (!cacheResult.success) {
//            // cache error should not block the main flow
//            log.error("cache get failed, cache_key: {}, result: {}", id, cacheResult);
//            if (Result.is(cacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
//                // delete the abnormal value to fast recover
//                log.error("invalid cache value, delete to fast recover, cache_key: {}, result: {}", cacheKey, cacheResult);
//                cacheReader.delete(cacheKey);
//            }
//        }
//        return cacheResult;
//    }
//}
