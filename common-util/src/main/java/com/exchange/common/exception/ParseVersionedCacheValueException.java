package com.exchange.common.exception;

public class ParseVersionedCacheValueException extends RuntimeException {
    public ParseVersionedCacheValueException(String invalidValueFormat) {
        super(invalidValueFormat);
    }
}
