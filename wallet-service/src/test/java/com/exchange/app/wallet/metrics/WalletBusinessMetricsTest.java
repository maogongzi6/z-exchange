package com.exchange.app.wallet.metrics;

import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import com.exchange.proto.common.error.ErrorPb;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletBusinessMetricsTest {
    private SimpleMeterRegistry registry;
    private WalletBusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WalletBusinessMetrics(registry);
    }

    @Test
    void recordsSuccessfulProtobufReply() {
        ErrorPb reply = metrics.recordProtobufRequest(
                WalletMetricTagValues.Operations.CREATE_WALLET,
                WalletMetricTagValues.Ingress.GRPC,
                PbErrorBuilder::success,
                value -> value
        );

        assertEquals(PbErrorBuilder.success(), reply);
        assertRequestCount(
                WalletMetricTagValues.Operations.CREATE_WALLET,
                WalletMetricTagValues.Ingress.GRPC,
                WalletMetricTagValues.Outcomes.SUCCESS,
                WalletMetricTagValues.ErrorCodes.OK,
                1.0
        );
    }

    @Test
    void recordsReturnedBusinessError() {
        metrics.recordProtobufRequest(
                WalletMetricTagValues.Operations.RESERVE_TRANSACTION,
                WalletMetricTagValues.Ingress.GRPC,
                () -> PbErrorBuilder.build(WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND),
                value -> value
        );

        assertRequestCount(
                WalletMetricTagValues.Operations.RESERVE_TRANSACTION,
                WalletMetricTagValues.Ingress.GRPC,
                WalletMetricTagValues.Outcomes.ERROR,
                WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND.getMessage(),
                1.0
        );
    }

    @Test
    void recordsThrownExceptionAndPropagatesIt() {
        IllegalStateException failure = new IllegalStateException("processor failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                metrics.<ErrorPb>recordProtobufRequest(
                        WalletMetricTagValues.Operations.ATOMIC_TRANSACTION,
                        WalletMetricTagValues.Ingress.GRPC,
                        () -> {
                            throw failure;
                        },
                        value -> value
                ));

        assertEquals(failure, thrown);
        assertRequestCount(
                WalletMetricTagValues.Operations.ATOMIC_TRANSACTION,
                WalletMetricTagValues.Ingress.GRPC,
                WalletMetricTagValues.Outcomes.ERROR,
                WalletServiceErrorCode.SERVER_ERROR.getMessage(),
                1.0
        );
    }

    @Test
    void recordsKafkaResultFailureUsingWalletBoundaryCode() {
        metrics.recordResult(
                WalletMetricTagValues.Operations.COMPLETE_LEDGER_TRANSACTION,
                WalletMetricTagValues.Ingress.KAFKA,
                () -> Result.failure(WalletServiceErrorCode.WALLET_TRANSACTION_UPDATE_FAILED, "conflict")
        );

        assertRequestCount(
                WalletMetricTagValues.Operations.COMPLETE_LEDGER_TRANSACTION,
                WalletMetricTagValues.Ingress.KAFKA,
                WalletMetricTagValues.Outcomes.ERROR,
                WalletServiceErrorCode.WALLET_TRANSACTION_UPDATE_FAILED.getMessage(),
                1.0
        );
    }

    private void assertRequestCount(String operation, String ingress, String outcome,
                                    String errorCode, double expected) {
        assertEquals(expected, registry.find(WalletMetrics.WALLET_REQUESTS.name())
                .tags(
                        WalletMetricTags.OPERATION, operation,
                        WalletMetricTags.INGRESS, ingress,
                        WalletMetricTags.OUTCOME, outcome,
                        WalletMetricTags.ERROR_CODE, errorCode
                )
                .counter()
                .count());
        assertEquals(expected, registry.find(WalletMetrics.WALLET_PROCESSING_DURATION.name())
                .tags(
                        WalletMetricTags.OPERATION, operation,
                        WalletMetricTags.INGRESS, ingress,
                        WalletMetricTags.OUTCOME, outcome
                )
                .timer()
                .count());
    }
}
