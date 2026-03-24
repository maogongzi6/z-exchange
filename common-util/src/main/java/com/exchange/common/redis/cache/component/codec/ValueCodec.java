package com.exchange.common.redis.cache.component.codec;

import com.exchange.common.exception.JsonParseException;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.component.JsonParser;
import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.cache.component.impl.CacheDecoder;
import com.exchange.common.redis.cache.component.impl.CacheEncoder;
import com.exchange.common.redis.cache.util.CacheContentValidator;
import com.exchange.common.utils.StringHelper;
import com.exchange.common.utils.ValidateHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ValueCodec implements CacheEncoder, CacheDecoder {
    final private JsonParser jsonHelper;

    public <T> String encode(T content, CacheType cacheType) throws CacheParseException {
        if (!CacheContentValidator.validateContent(content, cacheType)) {
            log.error("encode exception, empty content");
            throw new CacheParseException("encode exception, empty content");
        }

        String strContent = "";
        if (cacheType == CacheType.STRING) {
            if (content instanceof String) {
                strContent = (String) content;
            } else {
                log.error("encode exception, invalid cache type, content: {}, cache_type: {}, target_class: {}", content, cacheType, cacheType);
                throw new CacheParseException("invalid cache type, cache_type: " + cacheType + ", target_class: " + cacheType);
            }
        } else if (cacheType == CacheType.JSON) {
            try {
                strContent = jsonHelper.encode(content);
            } catch (JsonParseException e) {
                log.error("encode exception, parse json error: {}", content, e);
                throw new CacheParseException("encode exception, parse json error: " + content, e);
            }
        } else if (cacheType != CacheType.TOMBSTONE && cacheType != CacheType.NEGATIVE) {
            log.error("invalid cache type, content: {}, target_class: {}", content, cacheType);
            throw new CacheParseException("encode exception, invalid cache type, cache_type: " + cacheType);
        }
        return encodeValue(cacheType, strContent);
    }

    private String encodeValue(CacheType cacheType, String content) throws CacheParseException {
        return cacheType.marker + ":" + content;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheValueInfo<T> decode(String value, Class<T> clazz) throws CacheParseException {
        if (ValidateHelper.isEmpty(value)) {
            log.error("decode exception, empty value");
            throw new CacheParseException("decode exception, empty value");
        }

        Pair<String, String> pair = StringHelper.strictDivideIntoTwoParts(value, ":");
        CacheType cacheType = CacheType.mapper.from(pair.getFirst());
        String cacheContent = pair.getSecond();

        if (!CacheContentValidator.validateContent(cacheContent, cacheType)) {
            log.error("value divided error, invalid cache value, value: {}, target_class: {}", value, clazz);
            throw new CacheParseException("invalid cache value, value: " + value);
        }

        switch (cacheType) {
            case TOMBSTONE:
            case NEGATIVE:
                return new CacheValueInfo<>(null, cacheType);
            case STRING:
                if (clazz != String.class) {
                    log.error("invalid cache value, value: {}, target_class: {}, should be a String", value, clazz);
                    throw new CacheParseException("invalid cache value, value: " + value + ", target_class: " + clazz);
                }
                // safe cast, suppress the unchecked warning
                return new CacheValueInfo<>((T) cacheContent, CacheType.STRING);
            case JSON:
                try {
                    T obj = jsonHelper.decode(cacheContent, clazz);
                    return new CacheValueInfo<>(obj, CacheType.JSON);
                } catch (JsonParseException e) {
                    log.error("decode exception, parse json error: {}, class: {}", value, clazz, e);
                    throw new CacheParseException("decode exception, parse json error: " + value, e);
                }
            default:
                log.error("invalid cache value, value: {}, target_class: {}", value, clazz);
                throw new CacheParseException("invalid cache value, value: " + value + ", target_class: " + clazz);
        }
    }

    public CacheValueInfo<String> decode(String value) throws CacheParseException {
        return decode(value, String.class);
    }


}
