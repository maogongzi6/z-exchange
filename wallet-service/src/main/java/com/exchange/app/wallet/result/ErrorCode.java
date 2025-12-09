package com.exchange.app.wallet.result;

import com.exchange.common.result.PbMappableErrorCode;
import com.exchange.proto.common.error.ErrorCodePb;

public enum ErrorCode implements PbMappableErrorCode {
    SUCCESS(0, ErrorCodePb.ERROR_OK, "success"),
    UNKNOWN_ERROR(1, ErrorCodePb.ERROR_INTERNAL, "unknown_error"),
    SERVER_ERROR(2, ErrorCodePb.ERROR_INTERNAL,"server_error"),
    NULL_RESULT(3, ErrorCodePb.ERROR_INTERNAL, "null_result"),
    DB_ERROR(4, ErrorCodePb.ERROR_INTERNAL, "db_error"),

    INVALID_REQUEST_PARAMETER(100, ErrorCodePb.ERROR_INVALID_ARGUMENT, "invalid_request_parameter"),
    WALLET_NOT_FOUND(1000, ErrorCodePb.ERROR_NOT_FOUND,"wallet_not_found"),
    WALLET_DUPLICATED(1001, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_duplicated"),
    WALLET_STATUS_MISMATCH(1002, ErrorCodePb.ERROR_CONFLICT,"wallet_status_mismatch"),
    BALANCE_SNAPSHOT_DUPLICATED(1100, ErrorCodePb.ERROR_ALREADY_EXISTS, "balance_snapshot_duplicated"),
    WALLET_ACCOUNT_MAPPING_DUPLICATED(1200, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_account_mapping_duplicated"),
    CREATE_ACCOUNT_FAILED(10000, ErrorCodePb.ERROR_FAILED_PRECONDITION, "fail_to_create_account"),;

    final public int code;
    final ErrorCodePb protoCode;
    final public String message;

    ErrorCode(int code, ErrorCodePb protoCode, String message) {
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

    static public ErrorCode success() {
        return ErrorCode.SUCCESS;
    }
}
