package com.exchange.app.ledger.result;

import com.exchange.common.result.IResult;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.common.error.ErrorPb;

import java.util.Objects;

public class PbErrorBuilder {
    static public ErrorPb build(com.exchange.common.utils.result.Result<?> result) {
        Objects.requireNonNull(result, "result");
        return build(Results.getErrorCode(result), result.errorDetail());
    }

    static public ErrorPb build(IResult<?> result) {
        Objects.requireNonNull(result, "result");
        if (result.isSuccess()) {
            return success(result.getDetail());
        }
        return build(result.getErrorCode(), result.getDetail());
    }

    static public ErrorPb build(ErrorCode error) {
        return build(error, "");
    }

    static public ErrorPb build(ErrorCode error, String errorDetail) {
        Objects.requireNonNull(error, "error");
        return build(error.protoCode, error.getMessage(), errorDetail);
    }

    static public ErrorPb build(com.exchange.common.result.error.ErrorCode errorCode) {
        return build(errorCode, "");
    }

    static public ErrorPb build(com.exchange.common.result.error.ErrorCode errorCode, String errorDetail) {
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
