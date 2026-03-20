package com.exchange.common.exception;

public class JsonParseException extends RuntimeException {
    public JsonParseException(String detailMessage) {
        super(detailMessage);
    }

    public JsonParseException(String detailMessage, Throwable cause) {
        super(detailMessage, cause);
    }
}