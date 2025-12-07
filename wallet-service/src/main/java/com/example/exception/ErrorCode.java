package com.example.exception;

public enum ErrorCode {
    SUCCESS(0, "success"),
    UNKNOWN_ERROR(-1, "unknown_error"),
    SERVER_ERROR(-2, "server error"),
    BAD_REQUEST(1, "bad_request"),
    BAD_REPLY(2, "bad_reply"),
    INVALID_REQUEST_PARAMETERS(100, "invalid_request_parameters"),
    INVALID_DB_PARAMETERS(101, "invalid_db_parameters"),
    WALLET_NOT_FOUND(1000, "wallet_not_found"),
    WALLET_DUPLICATED(1001, "wallet_duplicated"),
    ENABLE_WALLET_FAILED(1002, "enable_wallet_failed"),
    BALANCE_SNAPSHOT_DUPLICATED(1100, "balance_snapshot_duplicated"),
    WALLET_ACCOUNT_MAPPING_DUPLICATED(1200, "wallet_account_mapping_duplicated"),
    CREATE_ACCOUNT_FAILED(10000, "fail_to_create_account"),;

    final public int code;
    final public String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
