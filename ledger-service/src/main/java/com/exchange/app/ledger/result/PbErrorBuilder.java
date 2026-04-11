package com.exchange.app.ledger.result;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.common.error.ErrorPb;

import java.util.Objects;

public class PbErrorBuilder {
    static public ErrorPb build(LedgerServiceErrorCode errorCode) {
        return build(errorCode, "");
    }

    static public ErrorPb build(LedgerServiceErrorCode errorCode, String errorDetail) {
        Objects.requireNonNull(errorCode, "errorCode");
        return build(ProtoErrorMapper.toProto(errorCode), errorCode.getMessage(), errorDetail);
    }

    static public ErrorPb success(String errorDetail) {
        return build(ErrorCodePb.ERROR_OK, "success", errorDetail);
    }

    static public ErrorPb success() {
        return success("");
    }

    private static ErrorPb build(ErrorCodePb errorCodePb, String message, String detail) {
        ErrorPb.Builder builder = ErrorPb.newBuilder();
        builder.setCode(errorCodePb)
                .setMessage(message)
                .setDetail(detail == null ? "" : detail);
        return builder.build();
    }
}
