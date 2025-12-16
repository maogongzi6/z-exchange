package com.exchange.app.wallet.result;

import com.exchange.common.result.PbMappableErrorCode;
import com.exchange.proto.common.error.ErrorCodePb;

public enum ErrorCode implements PbMappableErrorCode {
    SUCCESS(0, ErrorCodePb.ERROR_OK, "success"),
    UNKNOWN_ERROR(1, ErrorCodePb.ERROR_INTERNAL, "unknown_error"),
    SERVER_ERROR(2, ErrorCodePb.ERROR_INTERNAL,"server_error"),
    NULL_RESULT_ERROR(3, ErrorCodePb.ERROR_INTERNAL, "null_result_error"),
    DB_ERROR(4, ErrorCodePb.ERROR_INTERNAL, "db_error"),
    INVALID_VALUE_ERROR(5, ErrorCodePb.ERROR_INTERNAL, "invalid_value_error"),
    INVALID_ENUM_ERROR(6, ErrorCodePb.ERROR_INTERNAL, "invalid_enum_error"),

    INVALID_REQUEST_PARAMETER(100, ErrorCodePb.ERROR_INVALID_ARGUMENT, "invalid_request_parameter"),
    WALLET_NOT_FOUND(1000, ErrorCodePb.ERROR_NOT_FOUND,"wallet_not_found"),
    WALLET_DUPLICATED(1001, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_duplicated"),
    WALLET_UPDATE_FAILED(1002, ErrorCodePb.ERROR_CONFLICT,"wallet_update_failed"),
    WALLET_NOT_AVAILABLE(1003, ErrorCodePb.ERROR_FAILED_PRECONDITION, "wallet_not_available"),
    WALLET_INFO_MISMATCH(1004, ErrorCodePb.ERROR_FAILED_PRECONDITION, "wallet_info_mismatch"),
    BALANCE_SNAPSHOT_DUPLICATED(1100, ErrorCodePb.ERROR_ALREADY_EXISTS, "balance_snapshot_duplicated"),
    BALANCE_SNAPSHOT_NOT_FOUND(1101, ErrorCodePb.ERROR_NOT_FOUND, "balance_snapshot_not_found"),
    BALANCE_SNAPSHOT_UPDATE_FAILED(1102, ErrorCodePb.ERROR_INTERNAL, "balance_snapshot_update_failed"),
    WALLET_ACCOUNT_MAPPING_DUPLICATED(1200, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_account_mapping_duplicated"),
    WALLET_ACCOUNT_MAPPING_NOT_FOUND(1201,  ErrorCodePb.ERROR_NOT_FOUND, "wallet_account_mapping_not_found"),
    WALLET_TRANSACTION_DUPLICATED(1300, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_transaction_duplicated"),
    WALLET_TRANSACTION_NOT_FOUND(1301, ErrorCodePb.ERROR_NOT_FOUND, "wallet_transaction_not_found"),
    WALLET_TRANSACTION_INVALID(1302, ErrorCodePb.ERROR_INTERNAL, "wallet_transaction_invalid"),
    WALLET_TRANSACTION_UPDATE_FAILED(1303, ErrorCodePb.ERROR_INTERNAL, "wallet_transaction_update_failed"),
    WALLET_ACTION_DUPLICATION(1400, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_action_duplicated"),
    WALLET_ACTION_NOT_FOUND(1401, ErrorCodePb.ERROR_NOT_FOUND, "wallet_action_not_found"),
    WALLET_ACTION_INVALID(1402, ErrorCodePb.ERROR_INTERNAL, "wallet_action_invalid"),
    WALLET_ACTION_UPDATE_FAILED(1403, ErrorCodePb.ERROR_INTERNAL, "wallet_action_update_failed"),
    WALLET_RESERVATION_DUPLICATED(1500, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_reservation_duplicated"),
    WALLET_RESERVATION_NOT_FOUND(1501, ErrorCodePb.ERROR_NOT_FOUND, "wallet_reservation_not_found"),
    WALLET_RESERVATION_INVALID(1502, ErrorCodePb.ERROR_INTERNAL, "wallet_reservation_invalid"),
    WALLET_RESERVATION_UPDATE_FAILED(1503, ErrorCodePb.ERROR_INTERNAL, "wallet_reservation_update_failed"),
    WALLET_OUTBOX_DUPLICATED(1600, ErrorCodePb.ERROR_ALREADY_EXISTS, "wallet_outbox_duplicated"),
    WALLET_OUTBOX_UPDATE_FAILED(1601, ErrorCodePb.ERROR_INTERNAL, "wallet_outbox_update_failed"),

    CREATE_ACCOUNT_FAILED(10000, ErrorCodePb.ERROR_FAILED_PRECONDITION, "fail_to_create_account"),
    POST_TRANSACTION_FAILED(10001, ErrorCodePb.ERROR_FAILED_PRECONDITION, "fail_to_post_transaction"),
    ;

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
