package com.exchange.app.ledger.result;

import com.exchange.common.result.IResult;
import com.exchange.common.result.error.ErrorCode;
import com.exchange.common.result.error.ErrorNamespace;
import com.exchange.common.result.error.IdempErrorCode;
import com.exchange.common.result.error.OutboxErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Converts shared/common errors into the ledger-owned public error surface.
 * <p>
 * Business code may return LedgerServiceErrorCode directly when the public
 * ledger meaning is already known. Foreign/common errors are normalized here
 * so gRPC replies do not leak common-util implementation details.
 */
@Slf4j
public final class LedgerBoundaryErrorMapper {
    private LedgerBoundaryErrorMapper() {
    }

    public static LedgerServiceErrorCode toLedgerErrorCode(IResult<?> result) {
        Objects.requireNonNull(result, "result");
        if (result.isSuccess()) {
            throw new IllegalArgumentException("cannot map success result");
        }
        return toLedgerErrorCode(result.getErrorCode());
    }

    public static LedgerServiceErrorCode toLedgerErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            return LedgerServiceErrorCode.INTERNAL_ERROR;
        }

        if (errorCode instanceof LedgerServiceErrorCode ledgerServiceErrorCode) {
            return ledgerServiceErrorCode;
        }

        if (errorCode == IdempErrorCode.HASH_CONFLICT) {
            return LedgerServiceErrorCode.REQUEST_HASH_CONFLICT;
        }

        // These common-util failures indicate malformed internal state or infra
        // issues inside shared helpers, not caller-correctable ledger requests.
        if (ErrorNamespace.DB.equals(errorCode.getNamespace())
                || ErrorNamespace.REDIS.equals(errorCode.getNamespace())
                || ErrorNamespace.CACHE.equals(errorCode.getNamespace())
                || ErrorNamespace.KAFKA.equals(errorCode.getNamespace())
                || errorCode == OutboxErrorCode.UNEXPECTED_DB_ERROR) {
            return LedgerServiceErrorCode.INTERNAL_ERROR;
        }

        // Keep the ledger API surface stable even for newly introduced foreign
        // errors, but log the original metadata so the mapping gap is visible.
        log.warn(
                "unmapped ledger boundary error, fallback to internal_error, namespace={}, code={}, category={}, message={}",
                errorCode.getNamespace(),
                errorCode.getCode(),
                errorCode.getCategory(),
                errorCode.getMessage()
        );
        return LedgerServiceErrorCode.INTERNAL_ERROR;
    }
}
