package com.exchange.common.cache.utils;

import com.exchange.common.cache.constant.CommonIdempStatus;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.AllArgsConstructor;
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

    // idemp_v:{status}:{hash}:{token}
    static public String idempValue(CommonIdempStatus status, String hash, String token) {
        return idempValue(status, hash, token, null);
    }

    // idemp_v:{status}:{hash}:{token}:{value}
    static public String idempValue(CommonIdempStatus status, String hash, String token, String value) {
        if (Strings.isEmpty(value)) {
            return String.format("idemp_v:%s:%s:%s", status.code, hash, token);
        } else {
            return String.format("idemp_v:%s:%s:%s:%s", status.code, hash, token, value);
        }
    }

    static public Result<IdempKey> parseIdempKey(String key) {
        String[] k = key.split(":");
        if (k.length != 4) {
            log.error("parseIdempKey error, invalid key:{}", key);
            return Results.fail(CommonErrorCode.INVALID_IDEMP_KEY, "invalid idemp key:" + key);
        }
        return Results.success(new IdempKey(k[1], k[2], k[3]));
    }

    static public Result<IdempValue> parseIdempValue(String value) {
        String[] v = value.split(":");
        if (v.length < 4) {
            log.error("parseIdempValue error, invalid value:{}", value);
            return Results.fail(CommonErrorCode.INVALID_IDEMP_VALUE, "invalid idemp value: " + value);
        }
        CommonIdempStatus status = CommonIdempStatus.getByCode(v[1]);
        if (CommonIdempStatus.isUnknown(status)) {
            log.error("parseIdempValue error, invalid value, unknown status:{}", value);
            return Results.fail(CommonErrorCode.INVALID_IDEMP_VALUE, "invalid value, unknown status: " + value);
        }
        if (status == CommonIdempStatus.ACCEPTED && v.length != 5) {
            log.error("parseIdempValue error, empty value with accepted status:{}", value);
            return Results.fail(CommonErrorCode.INVALID_IDEMP_VALUE, "empty value with accepted status: " + value);
        }
        return Results.success(new IdempValue(status, v[2], v[3], status == CommonIdempStatus.ACCEPTED ? v[4] : null));
    }

}
