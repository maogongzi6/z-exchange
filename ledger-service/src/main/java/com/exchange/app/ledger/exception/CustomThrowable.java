package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class CustomThrowable extends RuntimeException {
    private ErrorCode code;
    public CustomThrowable(ErrorCode c) {
        super(c.message);
        code = c;
    }

    public CustomThrowable(ErrorCode c, String msg) {
        super(msg);
        code = c;
    }
}
