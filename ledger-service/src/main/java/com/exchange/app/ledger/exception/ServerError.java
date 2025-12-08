package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public class ServerError extends CustomThrowable {
    private ServerError(ErrorCode code) {
        super(code);
    }

}
