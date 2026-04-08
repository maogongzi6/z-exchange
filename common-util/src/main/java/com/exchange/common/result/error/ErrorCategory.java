package com.exchange.common.result.error;

public enum ErrorCategory {
    // internal
    INTERNAL,
    UNAVAILABLE,

    // business condition violations
    // use this error when you need a generic error for business logic but don't care about the specific reason
    FAILED_PRECONDITION,
    CONFLICT,
    // for example, another request with same params is in processing
    PROCESSING,

    // validation
    INVALID_ARGUMENT,
    UNAUTHENTICATED,
    UNAUTHORIZED,

    // resource issues
    NOT_FOUND,
    ALREADY_EXISTS,

}
