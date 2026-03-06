package com.exchange.common.cache.client;

import com.exchange.common.exception.ParseVersionedCacheValueException;
import com.exchange.common.utils.StringHelper;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Redis cache value format:
 * <p>
 *   {version}|{body}
 * <p>
 * Examples:
 *   12|J:{"txnId":"123","status":"DONE"}
 *   13|T
 * <p>
 * Meaning:
 * - {version} is the DB version of the business object.
 * - {body} encodes the cache state:
 *     - "J:{json}" = normal cached business payload
 *     - "T" = tombstone marker (payload intentionally removed)
 * <p>
 * Why this format:
 * - We store a version together with the cache value so Redis can perform
 *   compare-and-set (CAS) by version.
 * - Tombstone is used instead of deleting the key, so version information
 *   is preserved after DB update.
 * - This prevents an older DB read result from writing stale cache data
 *   back into Redis after a newer version has already invalidated the key.
 * <p>
 * Invariant:
 * - A cache writing is allowed only when incomingVersion > cachedVersion or key is absent.
 * - Therefore, an older value must never overwrite a newer payload or tombstone.
 * <p>
 * Notes:
 * - Redis is only a cache acceleration layer; DB remains the source of truth.
 * - Tombstone means "this key was invalidated at version V", not "record deleted".
 * <p>
 * See the design doc for more details.
 */

@Slf4j
@RequiredArgsConstructor
public class JsonVersionedCacheRedisClient {
    private static final String JsonPrefix = "J:";
    private static final String Tombstone = "T";

    private final VersionedCacheRedisClient versionedCacheRedisClient;
    private final ObjectMapper mapper;

    public <T> Result<T> get(String key, Class<T> clazz) {
        Result<String> valueResult = versionedCacheRedisClient.get(key);
        if (!valueResult.success) {
            log.error("Error getting value for key {}, error: {}", key, valueResult);
            return Results.fail(valueResult.errorCode, valueResult.errorDetail);
        }
        if (valueResult.value == null) {
            return Results.success(null);
        }
        String value = valueResult.value;

        T obj;
        try {
            obj = decode(value, clazz);
        } catch (JsonProcessingException | ParseVersionedCacheValueException e) {
            log.error("Error decoding value for key {}, value:{}: {}", key, value, e.getMessage());
            return Results.fail(CommonErrorCode.INVALID_VERSIONED_CACHE_VALUE, "Invalid value format");
        }
        return Results.success(obj);
    }

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T obj, long newVersion, Duration ttl) {
        String value;
        try {
            value = encode(obj, newVersion);
        } catch (JsonProcessingException e) {
            log.error("Error encoding value for key {}, obj:{}: {}", key, obj, e.getMessage());
            return Results.fail(CommonErrorCode.INVALID_VERSIONED_CACHE_VALUE, "failed to encode object");
        }
        return versionedCacheRedisClient.setIfAbsentOrNewer(key, value, newVersion, ttl);
    }

    public Result<Boolean> setTombstone(String key, long newVersion, Duration ttl) {
        String value = encodeTombstone(newVersion);
        return versionedCacheRedisClient.setIfAbsentOrNewer(key, value, newVersion, ttl);
    }

    public Boolean delete(String key) {
        return versionedCacheRedisClient.delete(key);
    }

    private <T> String encode(T obj, long version) throws JsonProcessingException {
        String json = mapper.writeValueAsString(obj);
        return String.format("%d|%s%s", version, JsonPrefix, json);
    }

    private String encodeTombstone(long version) {
        return String.format("%d|%s", version, Tombstone);
    }

    private <T> T decode(String value, Class<T> clazz) throws JsonProcessingException {
        // split to at most two parts, avoid destructing JSON part
        String[] parts = value.split("\\|", 2);
        if (parts.length < 2) {
            throw new ParseVersionedCacheValueException("Invalid value format");
        }
        // TODO ignore for now
        long version = Long.parseLong(parts[0]);

        return parseValueBody(parts[1], clazz);
    }

    private <T> T parseValueBody(String body, Class<T> clazz) throws JsonProcessingException {
        if (body.startsWith(JsonPrefix)) {
            String json = StringHelper.removePrefix(body, JsonPrefix);
            return mapper.readValue(json, clazz);
        } else if (body.equals(Tombstone)) {
            // return NULL if the value is tombstone
            return null;
        } else {
            throw new ParseVersionedCacheValueException("Invalid value format: " + body);
        }
    }
}
