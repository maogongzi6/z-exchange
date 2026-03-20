package com.exchange.common.exception;

public class CacheParseException extends Exception {
    public CacheParseException(String detailMessage) {
        super(detailMessage);
    }

    public CacheParseException(String detailMessage, Throwable cause) {
        super(detailMessage, cause);
    }
}