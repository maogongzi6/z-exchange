package com.exchange.app.wallet.metrics;

import com.exchange.app.wallet.result.WalletBoundaryErrorMapper;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.common.error.ErrorPb;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WalletBusinessMetrics {
    private final MeterRegistry registry;

    /**
     * Measures protobuf-returning wallet processors at the explicit service boundary.
     * The caller supplies the reply's error field so this component remains independent of a
     * particular RPC message while still classifying returned business failures.
     */
    public <T> T recordProtobufRequest(String operation, String ingress, Supplier<T> action,
                                       Function<T, ErrorPb> errorExtractor) {
        return recordBusinessRequest(operation, ingress, action,
                value -> fromProtobuf(value, errorExtractor));
    }

    /**
     * Measures transport-neutral Result-returning processors such as Kafka completion handling.
     */
    public <T> Result<T> recordResult(String operation, String ingress, Supplier<Result<T>> action) {
        return recordBusinessRequest(operation, ingress, action, this::fromResult);
    }

    private <T> T recordBusinessRequest(String operation, String ingress, Supplier<T> action,
                                        Function<T, BusinessOutcome> outcomeExtractor) {
        Timer.Sample sample = Timer.start(registry);
        // Thrown exceptions are recorded as bounded server failures and then propagated unchanged.
        BusinessOutcome outcome = BusinessOutcome.error(WalletServiceErrorCode.SERVER_ERROR.getMessage());
        try {
            T value = action.get();
            outcome = outcomeExtractor.apply(value);
            return value;
        } finally {
            recordRequest(operation, ingress, outcome);
            sample.stop(Timer.builder(WalletMetrics.WALLET_PROCESSING_DURATION.name())
                    .description(WalletMetrics.WALLET_PROCESSING_DURATION.description())
                    .tag(WalletMetricTags.OPERATION, operation)
                    .tag(WalletMetricTags.INGRESS, ingress)
                    .tag(WalletMetricTags.OUTCOME, outcome.outcome())
                    .register(registry));
        }
    }

    private void recordRequest(String operation, String ingress, BusinessOutcome outcome) {
        Counter.builder(WalletMetrics.WALLET_REQUESTS.name())
                .description(WalletMetrics.WALLET_REQUESTS.description())
                .tag(WalletMetricTags.OPERATION, operation)
                .tag(WalletMetricTags.INGRESS, ingress)
                .tag(WalletMetricTags.OUTCOME, outcome.outcome())
                .tag(WalletMetricTags.ERROR_CODE, outcome.errorCode())
                .register(registry)
                .increment();
    }

    private <T> BusinessOutcome fromProtobuf(T value, Function<T, ErrorPb> errorExtractor) {
        if (value == null) {
            return BusinessOutcome.error(WalletServiceErrorCode.SERVER_ERROR.getMessage());
        }
        ErrorPb error = errorExtractor.apply(value);
        if (error == null) {
            return BusinessOutcome.error(WalletServiceErrorCode.SERVER_ERROR.getMessage());
        }
        if (error.getCode() == ErrorCodePb.ERROR_OK) {
            return BusinessOutcome.success();
        }
        String errorCode = error.getMessage();
        if (errorCode == null || errorCode.isBlank()) {
            errorCode = error.getCode().name().toLowerCase(Locale.ROOT);
        }
        return BusinessOutcome.error(errorCode);
    }

    private BusinessOutcome fromResult(Result<?> result) {
        if (result == null) {
            return BusinessOutcome.error(WalletServiceErrorCode.SERVER_ERROR.getMessage());
        }
        if (result.isSuccess()) {
            return BusinessOutcome.success();
        }
        return BusinessOutcome.error(WalletBoundaryErrorMapper.toWalletErrorCode(result).getMessage());
    }

    private record BusinessOutcome(String outcome, String errorCode) {
        static BusinessOutcome success() {
            return new BusinessOutcome(WalletMetricTagValues.Outcomes.SUCCESS,
                    WalletMetricTagValues.ErrorCodes.OK);
        }

        static BusinessOutcome error(String errorCode) {
            return new BusinessOutcome(WalletMetricTagValues.Outcomes.ERROR,
                    errorCode == null || errorCode.isBlank() ? WalletMetricTagValues.UNKNOWN : errorCode);
        }
    }
}
