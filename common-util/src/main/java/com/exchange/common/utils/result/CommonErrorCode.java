package com.exchange.common.utils.result;

import com.exchange.proto.common.error.ErrorCodePb;

public enum CommonErrorCode implements PbMappableErrorCode{
    SUCCESS(0,ErrorCodePb.ERROR_OK, "success"),
    // common-util always return ErrorCodePb.ERROR_INTERNAL, service should convert it to specific code
    UNEXPECTED_DB_ERROR(-1,ErrorCodePb.ERROR_INTERNAL, "unexpected db error"),
    CACHE_CONNECTION_FAIL(-100, ErrorCodePb.ERROR_INTERNAL, "cache connection fail"),
    CACHE_CACHE_TIMEOUT(-101, ErrorCodePb.ERROR_INTERNAL, "cache connection timeout"),
    CACHE_ACCESS_FAIL(-102, ErrorCodePb.ERROR_INTERNAL, "cache access fail"),
    CACHE_SERIALIZATION_FAIL(-103, ErrorCodePb.ERROR_INTERNAL, "cache serialization fail"),
    CACHE_SCRIPT_FAIL(-104, ErrorCodePb.ERROR_INTERNAL, "cache script fail"),
    CACHE_CONFIG_FAIL(-105, ErrorCodePb.ERROR_INTERNAL, "cache config fail"),
    CACHE_VALUE_ENCODE_FAIL(-106, ErrorCodePb.ERROR_INTERNAL, "cache value encode fail"),
    CACHE_VALUE_DECODE_FAIL(-107, ErrorCodePb.ERROR_INTERNAL, "cache value decode fail"),
    CACHE_KEY_MALFORMED(-108, ErrorCodePb.ERROR_INTERNAL, "cache key malformed"),
    INVALID_IDEMP_KEY(-1000, ErrorCodePb.ERROR_INTERNAL, "invalid_idemp_key"),
    INVALID_IDEMP_VALUE(-1001, ErrorCodePb.ERROR_INTERNAL, "invalid_idemp_value"),
    PARSE_CACHE_ERROR(-1100, ErrorCodePb.ERROR_INTERNAL, "parse_cache_error"),
    ;

    final public int code;
    final public ErrorCodePb protoCode;
    final public String message;

    CommonErrorCode(int code, ErrorCodePb protoCode, String message) {
        this.code = code;
        this.protoCode = protoCode;
        this.message = message;
    }

    @Override
    public ErrorCodePb toProto() {
        return protoCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    static public CommonErrorCode success() {
        return CommonErrorCode.SUCCESS;
    }
}
