package com.exchange.app.ledger.exception;

public class ServerError extends CustomThrowable {
    private ServerError(ErrorCode code) {
        super(code);
    }

}
