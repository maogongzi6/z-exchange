package com.exchange.common.exception;

public class VersionedCacheParseException extends CacheParseException {
    public VersionedCacheParseException(String detailMessage) {
        super(detailMessage);
    }

    public VersionedCacheParseException(String detailMessage, Throwable cause) {
        super(detailMessage, cause);
    }
}
