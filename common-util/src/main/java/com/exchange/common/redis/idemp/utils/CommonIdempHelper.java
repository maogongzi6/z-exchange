package com.exchange.common.redis.idemp.utils;

import com.exchange.common.redis.idemp.constant.CommonIdempStatus;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.IdempErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

@Slf4j
public class CommonIdempHelper {
    // ":" as separator
    // idemp_k:{service}:{scope}:{idempId}
    static public String idempKey(String service, String scope, String idempId) {
        return String.format("idemp_k:%s:%s:%s", service, scope, idempId);
    }

    static public String idempPendingValue(String hash, String token) {
        return idempValue(CommonIdempStatus.PENDING, hash, token);
    }

    // {status}:{hash}:{token}
    static public String idempValue(CommonIdempStatus status, String hash, String token) {
        return idempValue(status, hash, token, null);
    }

    // {status}:{hash}:{token}:{value}
    static public String idempValue(CommonIdempStatus status, String hash, String token, String value) {
        if (Strings.isEmpty(value)) {
            return String.format("%s:%s:%s", status.code, hash, token);
        } else {
            return String.format("%s:%s:%s:%s", status.code, hash, token, value);
        }
    }

    static public Result<IdempKey> parseIdempKey(String key) {
        String[] k = key.split(":");
        if (k.length != 4) {
            log.error("parseIdempKey error, invalid key:{}", key);
            return Result.failure(IdempErrorCode.INVALID_IDEMP_KEY, "invalid idemp key:" + key);
        }
        return Result.success(new IdempKey(k[1], k[2], k[3]));
    }

    static public Result<IdempValue> parseIdempValue(String value) {
        String[] v = value.split(":");
        if (v.length < 3) {
            log.error("parseIdempValue error, invalid value:{}", value);
            return Result.failure(IdempErrorCode.INVALID_IDEMP_VALUE, "invalid idemp value: " + value);
        }
        CommonIdempStatus status = CommonIdempStatus.getByCode(v[0]);
        if (CommonIdempStatus.isUnknown(status)) {
            log.error("parseIdempValue error, invalid value, unknown status:{}", value);
            return Result.failure(IdempErrorCode.INVALID_IDEMP_VALUE, "invalid value, unknown status: " + value);
        }
        if (status == CommonIdempStatus.ACCEPTED && v.length != 4) {
            log.error("parseIdempValue error, empty value with accepted status:{}", value);
            return Result.failure(IdempErrorCode.INVALID_IDEMP_VALUE, "empty value with accepted status: " + value);
        }
        return Result.success(new IdempValue(status, v[1], v[2], status == CommonIdempStatus.ACCEPTED ? v[3] : null));
    }

}
