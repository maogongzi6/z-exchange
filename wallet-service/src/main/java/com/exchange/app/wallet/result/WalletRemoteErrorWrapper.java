package com.exchange.app.wallet.result;

import com.exchange.common.result.Result;
import com.exchange.proto.common.error.ErrorPb;

import java.util.Objects;

/**
 * Wraps remote service failures without exposing the upstream error as the
 * wallet public code. The upstream proto code/message/detail remain in the
 * local diagnostic detail for logs and support investigation.
 */
public final class WalletRemoteErrorWrapper {
    private WalletRemoteErrorWrapper() {
    }

    public static <T> Result<T> failure(WalletServiceErrorCode errorCode, String upstreamService, ErrorPb upstreamError) {
        Objects.requireNonNull(errorCode, "errorCode");
        return Result.failure(errorCode, buildDetail(upstreamService, upstreamError));
    }

    private static String buildDetail(String upstreamService, ErrorPb upstreamError) {
        if (upstreamError == null) {
            return "upstream_service=" + upstreamService + ", upstream_error=null";
        }
        return "upstream_service=" + upstreamService
                + ", upstream_code=" + upstreamError.getCode()
                + ", upstream_message=" + upstreamError.getMessage()
                + ", upstream_detail=" + upstreamError.getDetail();
    }
}
