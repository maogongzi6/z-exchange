package com.exchange.app.ledger.kafka.consumer;

import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.common.result.error.KafkaListenerErrorCode;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerFailureReplyFactoryTest {
    private static final String REQUEST_EVENT_ID = "wallet-event-123";
    private static final String COMMAND_ID = "wallet-command-123";

    private final LedgerFailureReplyFactory factory = new LedgerFailureReplyFactory();

    @Test
    void retryExhaustedKafkaFailureMapsToLedgerInternalReply() throws Exception {
        // KL-LH-008: common Kafka errors must not leak through the ledger public reply contract.
        ListenerFailure failure = ListenerFailure.retryExhausted(
                KafkaListenerErrorCode.RETRY_EXHAUSTED,
                "retry exhausted"
        );

        var outbox = factory.createFailureReply(envelope(), failure);

        assertEquals("reply:" + REQUEST_EVENT_ID + ":error:ledger:internal_error", outbox.getEventId());
        PostTransactionReplyPb reply = PostTransactionReplyPb.parseFrom(outbox.getPayload());
        assertEquals(ErrorCodePb.ERROR_INTERNAL, reply.getError().getCode());
        assertEquals(LedgerServiceErrorCode.INTERNAL_ERROR.getMessage(), reply.getError().getMessage());
        assertEquals("retry exhausted", reply.getError().getDetail());
    }

    @Test
    void configuredLedgerUnexpectedErrorCreatesLedgerFailureReply() throws Exception {
        // KL-LH-009: unexpected listener failures use the ledger error supplied by DefaultListener config.
        ListenerFailure failure = ListenerFailure.unexpected(
                LedgerServiceErrorCode.SERVER_ERROR,
                "unexpected listener failure",
                new NullPointerException("bug")
        );

        var outbox = factory.createFailureReply(envelope(), failure);

        PostTransactionReplyPb reply = PostTransactionReplyPb.parseFrom(outbox.getPayload());
        assertEquals("reply:" + REQUEST_EVENT_ID + ":error:ledger:server_error", outbox.getEventId());
        assertEquals(ErrorCodePb.ERROR_INTERNAL, reply.getError().getCode());
        assertEquals(LedgerServiceErrorCode.SERVER_ERROR.getMessage(), reply.getError().getMessage());
    }

    private EventEnvelopePb envelope() {
        return EventEnvelopePb.newBuilder()
                .setEventId(REQUEST_EVENT_ID)
                .setCommandId(COMMAND_ID)
                .setEventType("POST_LEDGER")
                .build();
    }
}
