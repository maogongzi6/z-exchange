package com.exchange.app.ledger.result;

import com.exchange.common.utils.result.Result;
import com.exchange.proto.common.error.ErrorPb;

public class PbErrorBuilder {
    static public ErrorPb build(Result<?> result) {
        return build(Results.getErrorCode(result), result.errorDetail);
    }

    static public ErrorPb build(ErrorCode error) {
        return build(error, "");
    }

    static public ErrorPb build(ErrorCode error, String errorDetail) {
        ErrorPb.Builder builder = ErrorPb.newBuilder();
        builder.setCode(error.protoCode).setMessage(error.getMessage()).setDetail(errorDetail);
        return builder.build();
    }

    static public ErrorPb success(String errorDetail) {
        return build(ErrorCode.SUCCESS, errorDetail);
    }

    static public ErrorPb success() {
        return build(ErrorCode.SUCCESS, "");
    }
}
