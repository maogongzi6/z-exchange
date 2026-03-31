package com.exchange.common.utils.result;

import com.exchange.proto.common.error.ErrorCodePb;

public enum CommonErrorCode implements PbMappableErrorCode{
    SUCCESS(0,ErrorCodePb.ERROR_OK, "success"),
    UNEXPECTED_DB_ERROR(-1,ErrorCodePb.ERROR_INTERNAL, "unexpected db error"),
    // common-util always return ErrorCodePb.ERROR_INTERNAL, service should convert it to specific code
    LUA_SCRIPT_EXECUTE_ERROR(-100, ErrorCodePb.ERROR_INTERNAL, "lua_script_execute_error"),
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
