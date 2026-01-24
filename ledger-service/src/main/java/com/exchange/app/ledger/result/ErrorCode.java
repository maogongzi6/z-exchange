package com.exchange.app.ledger.result;

import com.exchange.common.utils.result.PbMappableErrorCode;
import com.exchange.proto.common.error.ErrorCodePb;

public enum ErrorCode implements PbMappableErrorCode {
    SUCCESS(0, ErrorCodePb.ERROR_OK, "success"),
    UNKNOWN_ERROR(1, ErrorCodePb.ERROR_INTERNAL, "unknown_error"),
    SERVER_ERROR(2, ErrorCodePb.ERROR_INTERNAL,"server_error"),
    NULL_RESULT(3, ErrorCodePb.ERROR_INTERNAL, "null_result"),
    DB_ERROR(4, ErrorCodePb.ERROR_INTERNAL, "db_error"),
    SERIALIZE_ERROR(5, ErrorCodePb.ERROR_INTERNAL, "serialize_error"),
    INVALID_VALUE_ERROR(6, ErrorCodePb.ERROR_INTERNAL, "invalid_value_error"),
    INVALID_ENUM_ERROR(7, ErrorCodePb.ERROR_INTERNAL, "invalid_enum_error"),
    PUBLISH_KAFKA_ERROR(8, ErrorCodePb.ERROR_INTERNAL, "publish_kafka_error"),

    //    BAD_REQUEST(1, "bad_request"),
//    BAD_REPLY(2, "bad_reply"),
    INVALID_REQUEST_PARAMETER(100, ErrorCodePb.ERROR_INVALID_ARGUMENT, "invalid_request_parameter"),
//    INVALID_LEDGER_DIRECTION(1000, "invalid_ledger_direction"),
//    IMBALANCED_LEDGER_TXN(1010, "imbalanced_ledger_txn"),
    LEDGER_DUPLICATED(1020, ErrorCodePb.ERROR_ALREADY_EXISTS, "ledger_duplicated"),
    ACCOUNT_NOT_FOUND(1100, ErrorCodePb.ERROR_NOT_FOUND, "account_not_found"),
    ACCOUNT_DUPLICATED(1101, ErrorCodePb.ERROR_ALREADY_EXISTS, "account_duplicated"),
    ASSET_NOT_FOUND(1200, ErrorCodePb.ERROR_NOT_FOUND, "asset_not_found"),
    LEDGER_OUTBOX_DUPLICATED(2000, ErrorCodePb.ERROR_ALREADY_EXISTS, "ledger_outbox_duplicated"),;

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
