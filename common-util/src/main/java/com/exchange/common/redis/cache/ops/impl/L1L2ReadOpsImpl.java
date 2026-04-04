//package com.exchange.common.redis.cache.ops.impl;
//
//import com.exchange.common.component.PromotionClassifier;
//import com.exchange.common.redis.cache.component.support.ReadableCache;
//import com.exchange.common.redis.cache.model.CacheValueInfo;
//import com.exchange.common.redis.cache.ops.ReadOps;
//import com.exchange.common.utils.result.Result;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//@RequiredArgsConstructor
//public class L1L2ReadOpsImpl<T> implements ReadOps<T> {
//    private final ReadOps<T> l1CacheReader;
//    private final ReadOps<T> l2CacheReader;
//    private final PromotionClassifier classifier;
//
//    @Override
//    public Result<CacheValueInfo<T>> get(String id) {
//        Result<CacheValueInfo<T>> result;
//        if (classifier.isPromoted(id)) {
//            return l1CacheReader.get(id);
//        } else {
//            return l2CacheReader.get(id);
//        }
//    }
//}
