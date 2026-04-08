package com.exchange.app.ledger.exception;

import com.exchange.common.result.error.ErrorCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class CustomThrowable extends RuntimeException {
    private ErrorCode code;
    public CustomThrowable(ErrorCode c) {
        super(c.getMessage());
        code = c;
    }

    public CustomThrowable(ErrorCode c, String msg) {
        super(msg);
        code = c;
    }

    public CustomThrowable(ErrorCode c, Throwable cause) {
        super(c.getMessage(), cause);
        code = c;
    }

    public CustomThrowable(ErrorCode c, String msg, Throwable cause) {
        super(msg, cause);
        code = c;
    }
}
