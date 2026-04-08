package com.exchange.common.result.error;

import com.exchange.proto.common.error.ErrorCodePb;

public final class ProtoErrorMapper {
    private ProtoErrorMapper() {
    }

    public static ErrorCodePb toProto(ErrorCategory category) {
        if (category == null) {
            return ErrorCodePb.ERROR_INTERNAL;
        }

        return switch (category) {
            case INTERNAL -> ErrorCodePb.ERROR_INTERNAL;
            case UNAVAILABLE -> ErrorCodePb.ERROR_UNAVAILABLE;
            case FAILED_PRECONDITION -> ErrorCodePb.ERROR_FAILED_PRECONDITION;
            case CONFLICT -> ErrorCodePb.ERROR_CONFLICT;
            case PROCESSING -> ErrorCodePb.ERROR_PROCESSING;
            case INVALID_ARGUMENT -> ErrorCodePb.ERROR_INVALID_ARGUMENT;
            case UNAUTHENTICATED -> ErrorCodePb.ERROR_UNAUTHENTICATED;
            case UNAUTHORIZED -> ErrorCodePb.ERROR_UNAUTHORIZED;
            case NOT_FOUND -> ErrorCodePb.ERROR_NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCodePb.ERROR_ALREADY_EXISTS;
        };
    }

    public static ErrorCodePb toProto(ErrorCode errorCode) {
        if (errorCode == null) {
            return ErrorCodePb.ERROR_INTERNAL;
        }
        return toProto(errorCode.getCategory());
    }
}
