//package com.exchange.common.redis.cache.client;
//
//import com.exchange.common.redis.BaseClientSideCacheSupport;
//import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
//import com.exchange.common.redis.cache.component.factory.CacheReadSupportFactory;
//import com.exchange.common.redis.cache.component.support.CacheDecoderSupport;
//import com.exchange.common.redis.cache.model.CacheValueInfo;
//import com.exchange.common.utils.result.Result;
//
//public class VersionAppSideCacheReadClient implements CacheReader {
//    private final CacheDecoderSupport appSideCacheReadSupport;
//
//    public VersionAppSideCacheReadClient(
//            VersionCodec codec,
//            BaseClientSideCacheSupport<String> baseAppSideCacheSupport,
//            CacheReadSupportFactory factory) {
//        this.appSideCacheReadSupport = factory.create(codec, baseAppSideCacheSupport);
//    }
//
//    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
//        return appSideCacheReadSupport.get(key, clazz);
//    }
//}
