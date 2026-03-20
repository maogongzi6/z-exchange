//package com.exchange.common.cache.client.helper;
//
//import com.exchange.common.component.JsonConverter;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor(onConstructor_ = @Autowired)
//public class VersionJsonHelper {
//    final private VersionHelper versionHelper;
//    final private JsonConverter jsonHelper;
//
//    public <T> String encode(T obj, long version) {
//        return versionHelper.encode(jsonHelper.encode(obj), version);
//    }
//
//    public String encodeTombstone(long version) {
//        return versionHelper.encodeTombstone(version);
//    }
//
//    public <T> CacheValueInfo<T> decode(String value, Class<T> clazz) {
//        CacheValueInfo<String> stringVersionValue = versionHelper.decode(value);
//        T obj = jsonHelper.decode(stringVersionValue.value, clazz);
//        return new CacheValueInfo<>(stringVersionValue.version, obj);
//    }
//
//}
