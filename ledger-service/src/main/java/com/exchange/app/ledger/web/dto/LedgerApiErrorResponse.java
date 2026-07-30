package com.exchange.app.ledger.web.dto;

import com.exchange.app.ledger.result.LedgerServiceErrorCode;

public record LedgerApiErrorResponse(Error error) {
    public static LedgerApiErrorResponse from(LedgerServiceErrorCode errorCode, String detail) {
        return new LedgerApiErrorResponse(new Error(
                errorCode.getNamespace(),
                errorCode.getCode(),
                errorCode.getMessage(),
                detail
        ));
    }

    public record Error(
            String namespace,
            int code,
            String message,
            String detail
    ) {
    }
}
