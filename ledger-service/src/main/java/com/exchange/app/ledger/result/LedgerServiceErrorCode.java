package com.exchange.app.ledger.result;

import com.exchange.common.result.error.ErrorCategory;

public enum LedgerServiceErrorCode implements com.exchange.common.result.error.ErrorCode {
    INTERNAL_ERROR(2, ErrorCategory.INTERNAL, "internal_error"),
    SERVER_ERROR(3, ErrorCategory.INTERNAL, "server_error"),
    INVALID_REQUEST_PARAMETER(100, ErrorCategory.INVALID_ARGUMENT, "invalid_request_parameter"),
    ASSET_NOT_FOUND(1200, ErrorCategory.NOT_FOUND, "asset_not_found");

    private static final String NAMESPACE = "ledger";

    private final int code;
    private final ErrorCategory category;
    private final String message;

    LedgerServiceErrorCode(int code, ErrorCategory category, String message) {
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
