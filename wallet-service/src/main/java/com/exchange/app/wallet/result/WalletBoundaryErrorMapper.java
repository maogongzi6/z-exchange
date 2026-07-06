package com.exchange.app.wallet.result;

import com.exchange.common.result.IResult;
import com.exchange.common.result.error.ErrorCode;
import com.exchange.common.result.error.ErrorNamespace;
import com.exchange.common.result.error.IdempErrorCode;
import com.exchange.common.result.error.OutboxErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Converts shared/common errors into the wallet-owned public error surface.
 * <p>
 * Business code may return WalletServiceErrorCode directly when the public
 * wallet meaning is already known. Foreign/common errors are normalized here
 * so gRPC replies do not leak common-util implementation details.
 */
@Slf4j
public final class WalletBoundaryErrorMapper {
    private WalletBoundaryErrorMapper() {
    }

    public static WalletServiceErrorCode toWalletErrorCode(IResult<?> result) {
        Objects.requireNonNull(result, "result");
        if (result.isSuccess()) {
            throw new IllegalArgumentException("cannot map success result");
        }
        return toWalletErrorCode(result.getErrorCode());
    }

    public static WalletServiceErrorCode toWalletErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            return WalletServiceErrorCode.INTERNAL_ERROR;
        }

        if (errorCode instanceof WalletServiceErrorCode walletServiceErrorCode) {
            return walletServiceErrorCode;
        }

        if (errorCode == IdempErrorCode.HASH_CONFLICT) {
            return WalletServiceErrorCode.REQUEST_HASH_CONFLICT;
        }

        // These common-util failures indicate malformed internal state or infra
        // issues inside shared helpers, not caller-correctable wallet requests.
        if (ErrorNamespace.DB.equals(errorCode.getNamespace())
                || ErrorNamespace.REDIS.equals(errorCode.getNamespace())
                || ErrorNamespace.CACHE.equals(errorCode.getNamespace())
                || errorCode == OutboxErrorCode.UNEXPECTED_DB_ERROR) {
            return WalletServiceErrorCode.INTERNAL_ERROR;
        }

        // Keep the wallet API surface stable even for newly introduced foreign
        // errors, but log the original metadata so the mapping gap is visible.
        log.warn(
                "unmapped wallet boundary error, fallback to internal_error, namespace={}, code={}, category={}, message={}",
                errorCode.getNamespace(),
                errorCode.getCode(),
                errorCode.getCategory(),
                errorCode.getMessage()
        );
        return WalletServiceErrorCode.INTERNAL_ERROR;
    }
}
