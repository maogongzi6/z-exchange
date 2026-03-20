package com.exchange.common.redis.cache.component.parser;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.exception.VersionedCacheParseException;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.impl.CacheDecoder;
import com.exchange.common.redis.cache.impl.VersionCacheEncoder;
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
public class VersionParser implements VersionCacheEncoder, CacheDecoder {
    private final ValueParser valueParseHelper;

    public <T> String encode(T value, CacheType cacheType, long version) throws CacheParseException {
        if (ValidateHelper.isEmpty(value)) {
            log.error("encode exception, empty value");
            throw new VersionedCacheParseException("Empty value");
        }
        if (version < 0) {
            log.error("encode exception, version is negative, version is {}", version);
            throw new VersionedCacheParseException("Negative version");
        }
        String encoded = valueParseHelper.encode(value, cacheType);
        return String.format("%d|%s", version, encoded);
    }

    public CacheValueInfo<String> decode(String value) throws CacheParseException {
        return decode(value, String.class);
    }

    public <T> CacheValueInfo<T> decode(String value, Class<T> clazz) throws CacheParseException {
        if (ValidateHelper.isEmpty(value)) {
            log.error("decode exception, empty value");
            throw new VersionedCacheParseException("Empty value");
        }

        Pair<String, Long> pair = divide(value);
        String content = pair.getFirst();
        long version = pair.getSecond();
        CacheValueInfo<T> decoded = valueParseHelper.decode(content, clazz);
        return new CacheValueInfo<>(decoded.value, decoded.cacheType, version);
    }

    private Pair<String, Long> divide(String value) throws CacheParseException {
        // divide to version and content
        Pair<String, String> pair = StringHelper.strictDivideIntoTwoParts(value, "|");
        if (ValidateHelper.isEmpty(pair.getFirst()) || ValidateHelper.isEmpty(pair.getSecond())) {
            log.error("decode exception, invalid value format: {}", value);
            throw new VersionedCacheParseException("decode exception, invalid value format");
        }
        long version;
        try {
            version = Long.parseLong(pair.getFirst());
        } catch (NumberFormatException e) {
            log.error("decode str exception, parse long fails, invalid value: {}", value);
            throw new VersionedCacheParseException("decode exception, version format is invalid", e);
        }
        return Pair.of(pair.getSecond(), version);
    }
}
