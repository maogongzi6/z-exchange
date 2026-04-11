package com.exchange.common.result.error;

public interface ErrorCode {
    int getCode();
    String getMessage();
    ErrorCategory getCategory();
    String getNamespace();
}
