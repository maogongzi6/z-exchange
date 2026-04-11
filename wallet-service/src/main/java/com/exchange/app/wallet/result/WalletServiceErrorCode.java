package com.exchange.app.wallet.result;

import com.exchange.common.result.error.ErrorCategory;
import com.exchange.common.result.error.ErrorCode;

public enum WalletServiceErrorCode implements ErrorCode {
    INTERNAL_ERROR(1, ErrorCategory.INTERNAL, "internal_error"),
    SERVER_ERROR(2, ErrorCategory.INTERNAL, "server_error"),
    NULL_RESULT_ERROR(3, ErrorCategory.INTERNAL, "null_result_error"),
    DB_ERROR(4, ErrorCategory.INTERNAL, "db_error"),
    DB_STATE_BROKEN(5, ErrorCategory.INTERNAL, "db_state_broken"),
    INVALID_VALUE_ERROR(6, ErrorCategory.INTERNAL, "invalid_value_error"),
    INVALID_ENUM_ERROR(7, ErrorCategory.INTERNAL, "invalid_enum_error"),
    PUBLISH_KAFKA_ERROR(8, ErrorCategory.INTERNAL, "publish_kafka_error"),
    SERIALIZE_ERROR(9, ErrorCategory.INTERNAL, "serialize_error"),

    INVALID_REQUEST_PARAMETER(100, ErrorCategory.INVALID_ARGUMENT, "invalid_request_parameter"),
    REQUEST_HASH_CONFLICT(200, ErrorCategory.FAILED_PRECONDITION, "request_hash_conflict"),
    REQUEST_IN_PROCESSING(201, ErrorCategory.PROCESSING, "request_in_processing"),
    WALLET_NOT_FOUND(1000, ErrorCategory.NOT_FOUND, "wallet_not_found"),
    WALLET_DUPLICATED(1001, ErrorCategory.ALREADY_EXISTS, "wallet_duplicated"),
    WALLET_UPDATE_FAILED(1002, ErrorCategory.CONFLICT, "wallet_update_failed"),
    WALLET_NOT_AVAILABLE(1003, ErrorCategory.FAILED_PRECONDITION, "wallet_not_available"),
    WALLET_INFO_MISMATCH(1004, ErrorCategory.FAILED_PRECONDITION, "wallet_info_mismatch"),
    BALANCE_SNAPSHOT_DUPLICATED(1100, ErrorCategory.ALREADY_EXISTS, "balance_snapshot_duplicated"),
    BALANCE_SNAPSHOT_NOT_FOUND(1101, ErrorCategory.NOT_FOUND, "balance_snapshot_not_found"),
    BALANCE_SNAPSHOT_UPDATE_FAILED(1102, ErrorCategory.FAILED_PRECONDITION, "balance_snapshot_update_failed"),
    BALANCE_SNAPSHOT_MISMATCH(1103, ErrorCategory.FAILED_PRECONDITION, "balance_snapshot_mismatch"),
    WALLET_ACCOUNT_MAPPING_DUPLICATED(1200, ErrorCategory.ALREADY_EXISTS, "wallet_account_mapping_duplicated"),
    WALLET_ACCOUNT_MAPPING_NOT_FOUND(1201, ErrorCategory.NOT_FOUND, "wallet_account_mapping_not_found"),
    WALLET_TRANSACTION_DUPLICATED(1300, ErrorCategory.ALREADY_EXISTS, "wallet_transaction_duplicated"),
    WALLET_TRANSACTION_NOT_FOUND(1301, ErrorCategory.NOT_FOUND, "wallet_transaction_not_found"),
    WALLET_TRANSACTION_INVALID(1302, ErrorCategory.FAILED_PRECONDITION, "wallet_transaction_invalid"),
    WALLET_TRANSACTION_UPDATE_FAILED(1303, ErrorCategory.FAILED_PRECONDITION, "wallet_transaction_update_failed"),
    WALLET_ACTION_DUPLICATION(1400, ErrorCategory.ALREADY_EXISTS, "wallet_action_duplicated"),
    WALLET_ACTION_NOT_FOUND(1401, ErrorCategory.NOT_FOUND, "wallet_action_not_found"),
    WALLET_ACTION_INVALID(1402, ErrorCategory.FAILED_PRECONDITION, "wallet_action_invalid"),
    WALLET_ACTION_MISMATCH(1403, ErrorCategory.FAILED_PRECONDITION, "wallet_action_mismatch"),
    WALLET_ACTION_UPDATE_FAILED(1404, ErrorCategory.FAILED_PRECONDITION, "wallet_action_update_failed"),
    WALLET_RESERVATION_DUPLICATED(1500, ErrorCategory.ALREADY_EXISTS, "wallet_reservation_duplicated"),
    WALLET_RESERVATION_NOT_FOUND(1501, ErrorCategory.NOT_FOUND, "wallet_reservation_not_found"),
    WALLET_RESERVATION_INVALID(1502, ErrorCategory.FAILED_PRECONDITION, "wallet_reservation_invalid"),
    WALLET_RESERVATION_MISMATCH(1503, ErrorCategory.FAILED_PRECONDITION, "wallet_reservation_mismatch"),
    WALLET_RESERVATION_UPDATE_FAILED(1504, ErrorCategory.FAILED_PRECONDITION, "wallet_reservation_update_failed"),
    WALLET_RESERVATION_NOT_SATISFIED(1505, ErrorCategory.FAILED_PRECONDITION, "wallet_reservation_not_satisfied"),
    WALLET_RESERVATION_NOT_AVAILABLE(1506, ErrorCategory.FAILED_PRECONDITION, "wallet_reservation_not_available"),
    WALLET_OUTBOX_DUPLICATED(1600, ErrorCategory.ALREADY_EXISTS, "wallet_outbox_duplicated"),
    WALLET_OUTBOX_UPDATE_FAILED(1601, ErrorCategory.FAILED_PRECONDITION, "wallet_outbox_update_failed"),

    CREATE_ACCOUNT_FAILED(10000, ErrorCategory.FAILED_PRECONDITION, "fail_to_create_account"),
    POST_TRANSACTION_FAILED(10001, ErrorCategory.FAILED_PRECONDITION, "fail_to_post_transaction");

    private static final String NAMESPACE = "wallet";

    private final int code;
    private final ErrorCategory category;
    private final String message;

    WalletServiceErrorCode(int code, ErrorCategory category, String message) {
        this.code = code;
        this.category = category;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorCategory getCategory() {
        return category;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }
}
