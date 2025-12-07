package com.example.exception;

public enum ErrorCode {
    SUCCESS(0, "success"),
    UNKNOWN_ERROR(-1, "unknown_error"),
    SERVER_ERROR(-2, "server error"),
    BAD_REQUEST(1, "bad_request"),
    BAD_REPLY(2, "bad_reply"),
    INVALID_REQUEST_PARAMETERS(100, "invalid_request_parameters"),
//    INVALID_LEDGER_DIRECTION(1000, "invalid_ledger_direction"),
//    IMBALANCED_LEDGER_TXN(1010, "imbalanced_ledger_txn"),
    LEDGER_DUPLICATED(1020, "ledger_duplicated"),
    ACCOUNT_NOT_FOUND(1100, "account_not_found"),
    ACCOUNT_DUPLICATED(1101, "account_duplicated"),
    ASSET_NOT_FOUND(1200, "asset_not_found"),;

    final public int code;
    final public String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
