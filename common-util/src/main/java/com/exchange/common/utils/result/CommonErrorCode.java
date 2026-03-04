package com.exchange.common.utils.result;

import com.exchange.proto.common.error.ErrorCodePb;

public enum CommonErrorCode implements PbMappableErrorCode{
    SUCCESS(0,ErrorCodePb.ERROR_OK, "success"),
    // common-util always return ErrorCodePb.ERROR_INTERNAL, service should convert it to specific code
    INVALID_IDEMP_KEY(-1, ErrorCodePb.ERROR_INTERNAL, "invalid_idemp_key"),
    INVALID_IDEMP_VALUE(-2, ErrorCodePb.ERROR_INTERNAL, "invalid_idemp_value"),
    LUA_SCRIPT_EXECUTE_ERROR(-3, ErrorCodePb.ERROR_INTERNAL, "lua_script_execute_error"),
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
