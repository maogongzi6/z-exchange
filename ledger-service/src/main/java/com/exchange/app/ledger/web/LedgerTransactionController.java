package com.exchange.app.ledger.web;

import com.exchange.app.ledger.metrics.LedgerBusinessMetrics;
import com.exchange.app.ledger.metrics.LedgerMetricTagValues;
import com.exchange.app.ledger.processor.post.GetLedgerTxnProcessor;
import com.exchange.app.ledger.result.LedgerBoundaryErrorMapper;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.web.dto.LedgerApiErrorResponse;
import com.exchange.app.ledger.web.dto.LedgerTransactionResponse;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.ErrorCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ledger/transactions")
@RequiredArgsConstructor
public class LedgerTransactionController {
    private final GetLedgerTxnProcessor getLedgerTxnProcessor;
    private final LedgerBusinessMetrics ledgerBusinessMetrics;

    @GetMapping("/{ledgerTxnId}")
    public ResponseEntity<Object> getByLedgerTxnId(
            @PathVariable String ledgerTxnId,
            @RequestParam(defaultValue = "false") boolean includeEntries) {
        return query(
                GetLedgerTxnProcessor.LookupType.TXN_ID,
                ledgerTxnId,
                includeEntries,
                LedgerMetricTagValues.Operations.GET_LEDGER_TXN_BY_ID
        );
    }

    @GetMapping(params = "referenceId")
    public ResponseEntity<Object> getByReferenceId(
            @RequestParam String referenceId,
            @RequestParam(defaultValue = "false") boolean includeEntries) {
        return query(
                GetLedgerTxnProcessor.LookupType.REF_ID,
                referenceId,
                includeEntries,
                LedgerMetricTagValues.Operations.GET_LEDGER_TXN_BY_REF
        );
    }

    private ResponseEntity<Object> query(
            GetLedgerTxnProcessor.LookupType lookupType,
            String lookupValue,
            boolean includeEntries,
            String metricOperation) {
        try {
            // Keep HTTP and gRPC on the same processor and business metric boundary. The ingress
            // tag distinguishes transport cost while preserving comparable processor measurements.
            Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = ledgerBusinessMetrics.recordLedgerTxnQuery(
                    metricOperation,
                    LedgerMetricTagValues.Ingress.HTTP,
                    () -> getLedgerTxnProcessor.getLedgerTxn(lookupType, lookupValue, includeEntries)
            );
            if (result.isSuccess()) {
                return ResponseEntity.ok(LedgerTransactionResponse.from(result.getValue()));
            }

            LedgerServiceErrorCode errorCode = LedgerBoundaryErrorMapper.toLedgerErrorCode(result);
            return ResponseEntity.status(httpStatus(errorCode))
                    .body(LedgerApiErrorResponse.from(errorCode, publicDetail(errorCode, result.getDetail())));
        } catch (Exception exception) {
            log.error("uncaught HTTP ledger transaction query exception, lookupType={}", lookupType, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LedgerApiErrorResponse.from(LedgerServiceErrorCode.SERVER_ERROR, null));
        }
    }

    private HttpStatus httpStatus(LedgerServiceErrorCode errorCode) {
        ErrorCategory category = errorCode.getCategory();
        return switch (category) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case UNAUTHORIZED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED_PRECONDITION, CONFLICT, PROCESSING, ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String publicDetail(LedgerServiceErrorCode errorCode, String detail) {
        return errorCode.getCategory() == ErrorCategory.INTERNAL ? null : detail;
    }
}
