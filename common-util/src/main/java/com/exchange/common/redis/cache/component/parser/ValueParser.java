package com.exchange.common.redis.cache.component.parser;

import com.exchange.common.exception.JsonParseException;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.component.JsonParser;
import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.cache.impl.CacheDecoder;
import com.exchange.common.redis.cache.impl.CacheEncoder;
import com.exchange.common.utils.StringHelper;
import com.exchange.common.utils.ValidateHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ValueParser implements CacheEncoder, CacheDecoder {
    final private JsonParser jsonHelper;

    public <T> String encode(T value, CacheType cacheType) throws CacheParseException {
        if (ValidateHelper.isEmpty(value)) {
            log.error("encode exception, empty value");
            throw new CacheParseException("encode exception, empty value");
        }

        switch (cacheType) {
            case TOMBSTONE:
            case NEGATIVE:
                return encodeValue(cacheType, CacheType.PLACEHOLDER);
            case STRING:
                if (value instanceof String) {
                    return encodeValue(CacheType.STRING, (String) value);
                } else {
                    log.error("encode exception, invalid cache type, value: {}, cache_type: {}, target_class: {}", value, cacheType, cacheType);
                    throw new CacheParseException("invalid cache type, cache_type: " + cacheType + ", target_class: " + cacheType);
                }
            case JSON:
                String json;
                try {
                    json = jsonHelper.encode(value);
                } catch (JsonParseException e) {
                    log.error("encode exception, parse json error: {}", value, e);
                    throw new CacheParseException("encode exception, parse json error: " + value, e);
                }
                return encodeValue(CacheType.JSON, json);
            default:
                throw new CacheParseException("encode exception, invalid cache type, cache_type: " + cacheType);
        }
    }

    private String encodeValue(CacheType cacheType, String value) throws CacheParseException {
        return cacheType.marker + ":" + value;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheValueInfo<T> decode(String value, Class<T> clazz) throws CacheParseException {
        if (ValidateHelper.isEmpty(value)) {
            log.error("decode exception, empty value");
            throw new CacheParseException("decode exception, empty value");
        }

        Pair<String, String> pair = StringHelper.strictDivideIntoTwoParts(value, ":");
        String cacheMarker = pair.getFirst();
        String cacheContent = pair.getSecond();

        if (ValidateHelper.isEmpty(cacheMarker) || ValidateHelper.isEmpty(cacheContent)) {
            log.error("value divided error, invalid cache value, value: {}, target_class: {}", value, clazz);
            throw new CacheParseException("invalid cache value, value: " + value);
        }

        if (Objects.equals(cacheMarker, CacheType.TOMBSTONE.marker)) {
            return new CacheValueInfo<>(null, CacheType.TOMBSTONE);
        } else if (Objects.equals(cacheMarker, CacheType.NEGATIVE.marker)) {
            return new CacheValueInfo<>(null, CacheType.NEGATIVE);
        } else if (Objects.equals(cacheMarker, CacheType.STRING.marker)) {
            if (clazz != String.class) {
                log.error("invalid cache value, value: {}, target_class: {}, should be a String", value, clazz);
                throw new CacheParseException("invalid cache value, value: " + value + ", target_class: " + clazz);
            }
            // safe cast, suppress the unchecked warning
            return new CacheValueInfo<>((T) cacheContent, CacheType.STRING);
        } else if (Objects.equals(cacheMarker, CacheType.JSON.marker)) {
            T obj;
            try {
                obj = jsonHelper.decode(cacheContent, clazz);
            } catch (JsonParseException e) {
                log.error("decode exception, parse json error: {}, class: {}", value, clazz, e);
                throw new CacheParseException("decode exception, parse json error: " + value, e);
            }
            return new CacheValueInfo<>(obj, CacheType.JSON);
        } else {
            log.error("invalid cache value, value: {}, target_class: {}", value, clazz);
            throw new CacheParseException("invalid cache value, value: " + value + ", target_class: " + clazz);
        }
    }

    public CacheValueInfo<String> decode(String value) throws CacheParseException {
        return decode(value, String.class);
    }

//    private String decodeValue(CacheType cacheType, String value) {
//        if (cacheType == CacheType.TOMBSTONE || cacheType == CacheType.NEGATIVE) {
//            log.error("decode_value:wrong use of cache type, {}, value: {}", cacheType, value);
//            throw new CacheParseException("decode_value:wrong use of cache type, " + cacheType);
//        }
//
//        String p = prefix(cacheType);
//        String content;
//        if (value.startsWith(p)) {
//            content = value.substring(p.length());
//        } else {
//            log.error("invalid cache value format: {}, cache_type: {}", value, cacheType);
//            throw new CacheParseException("Invalid cache value format: " + value);
//        }
//
//        return content;
//    }

}
