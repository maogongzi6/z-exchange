package com.exchange.common.redis.cache.component.codec.impl;

import com.exchange.common.exception.CacheException;
import com.exchange.common.exception.JsonParseException;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.redis.cache.component.codec.CacheEncoder;
import com.exchange.common.component.JsonParser;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.model.CacheReadResult;
import com.exchange.common.redis.cache.util.CacheContentValidator;
import com.exchange.common.result.error.CacheErrorCode;
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

    public <T> String encode(T content, CacheType cacheType) {
        if (cacheType == null || !CacheContentValidator.validateContent(content, cacheType)) {
            log.error("encode exception, invalid content, content: {}, cacheType: {}", content, cacheType);
            throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                    "invalid cache content for encode, cacheType: " + cacheType);
        }

        String strContent = "";
        if (cacheType == CacheType.STRING) {
            if (content instanceof String) {
                strContent = (String) content;
            } else {
                log.error("encode exception, invalid cache type, content: {}, cache_type: {}, target_class: {}", content, cacheType, cacheType);
                throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                        "invalid cache type for encode, cache_type: " + cacheType);
            }
        } else if (cacheType == CacheType.JSON) {
            try {
                strContent = jsonHelper.encode(content);
            } catch (JsonParseException e) {
                log.error("encode exception, parse json error: {}", content, e);
                throw new CacheException(CacheErrorCode.SERIALIZATION,
                        "encode exception, parse json error: " + content, e);
            }
        } else if (cacheType != CacheType.TOMBSTONE && cacheType != CacheType.NEGATIVE) {
            log.error("invalid cache type, content: {}, target_class: {}", content, cacheType);
            throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                    "encode exception, invalid cache type, cache_type: " + cacheType);
        }
        return encodeValue(cacheType, strContent);
    }

    private String encodeValue(CacheType cacheType, String content) {
        return cacheType.marker + ":" + content;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheReadResult<T> decode(String value, Class<T> clazz) {
        if (ValidateHelper.isEmpty(value)) {
            log.error("decode exception, empty value");
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE, "decode exception, empty value");
        }

        Pair<String, String> pair = StringHelper.strictDivideIntoTwoParts(value, ":");
        CacheType cacheType = CacheType.mapper.from(pair.getFirst());
        if (cacheType == null) {
            log.error("decode exception, invalid cache marker, value: {}, target_class: {}", value, clazz);
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE,
                    "invalid cache marker, value: " + value + ", target_class: " + clazz);
        }
        String cacheContent = pair.getSecond();

        if (!CacheContentValidator.validateContent(cacheContent, cacheType)) {
            log.error("value divided error, invalid cache value, value: {}, target_class: {}", value, clazz);
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE, "invalid cache value, value: " + value);
        }

        switch (cacheType) {
            case TOMBSTONE:
                return CacheReadResult.tombstoneHit();
            case NEGATIVE:
                return CacheReadResult.negativeHit();
            case STRING:
                if (clazz != String.class) {
                    log.error("invalid cache value, value: {}, target_class: {}, should be a String", value, clazz);
                    throw new CacheException(CacheErrorCode.MALFORMED_VALUE,
                            "invalid cache value, value: " + value + ", target_class: " + clazz);
                }
                // safe cast, suppress the unchecked warning
                return CacheReadResult.valueHit((T) cacheContent);
            case JSON:
                try {
                    T obj = jsonHelper.decode(cacheContent, clazz);
                    return CacheReadResult.valueHit(obj);
                } catch (JsonParseException e) {
                    log.error("decode exception, parse json error: {}, class: {}", value, clazz, e);
                    throw new CacheException(CacheErrorCode.SERIALIZATION,
                            "decode exception, parse json error: " + value, e);
                }
            default:
                log.error("invalid cache value, value: {}, target_class: {}", value, clazz);
                throw new CacheException(CacheErrorCode.MALFORMED_VALUE,
                        "invalid cache value, value: " + value + ", target_class: " + clazz);
        }
    }

    public CacheReadResult<String> decode(String value) {
        return decode(value, String.class);
    }
}
