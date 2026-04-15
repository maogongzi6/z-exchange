package com.exchange.common.redis.cache.component.codec.impl;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.redis.cache.component.codec.VersionCacheEncoder;
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
public class VersionCodec implements VersionCacheEncoder, CacheDecoder {
    private final ValueCodec valueParseHelper;

    public <T> String encode(T content, CacheType cacheType, long version) {
        if (cacheType == null || !CacheContentValidator.validateContent(content, cacheType)) {
            log.error("encode exception, invalid content, content: {}, cacheType: {}", content, cacheType);
            throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                    "invalid versioned cache content for encode, cacheType: " + cacheType);
        }
        if (version < 0) {
            log.error("encode exception, version is negative, version is {}", version);
            throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                    "invalid versioned cache content, negative version");
        }
        String encoded = valueParseHelper.encode(content, cacheType);
        return String.format("%d|%s", version, encoded);
    }

    public CacheReadResult<String> decode(String value) {
        return decode(value, String.class);
    }

    public <T> CacheReadResult<T> decode(String value, Class<T> clazz) {
        if (ValidateHelper.isEmpty(value)) {
            log.error("decode exception, empty value");
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE, "Empty value");
        }

        Pair<String, Long> pair = divide(value);
        String content = pair.getFirst();
        long version = pair.getSecond();
        CacheReadResult<T> decoded = valueParseHelper.decode(content, clazz);
        return new CacheReadResult<>(decoded.value(), decoded.status(), version);
    }

    private Pair<String, Long> divide(String value) {
        // divide to version and content
        Pair<String, String> pair = StringHelper.strictDivideIntoTwoParts(value, "|");
        if (ValidateHelper.isEmpty(pair.getFirst()) || ValidateHelper.isEmpty(pair.getSecond())) {
            log.error("decode exception, invalid value format: {}", value);
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE,
                    "decode exception, invalid value format");
        }
        long version;
        try {
            version = Long.parseLong(pair.getFirst());
        } catch (NumberFormatException e) {
            log.error("decode str exception, parse long fails, invalid value: {}", value);
            throw new CacheException(CacheErrorCode.MALFORMED_VALUE,
                    "decode exception, version format is invalid", e);
        }
        return Pair.of(pair.getSecond(), version);
    }
}
