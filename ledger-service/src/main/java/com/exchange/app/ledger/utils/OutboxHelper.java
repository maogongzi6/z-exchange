package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.constant.EventType;
import com.exchange.app.ledger.kafka.constant.LedgerTopic;
import com.exchange.app.ledger.po.enums.outbox.OutboxEventType;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.result.PostTransactionResult;
import com.exchange.app.ledger.result.PostTransactionResultConverter;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.enums.EnumMapper;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;

import java.time.LocalDateTime;
import java.util.HashMap;

public class OutboxHelper {
    public static String resolveEventType(int eventType) {
        return outboxEventTypeStringMapper.to(eventType);
    }

    final static public EnumMapper<Integer, OutboxEventType> outboxEventTypeMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OutboxEventType.LEDGER_POST_REPLY.code, OutboxEventType.LEDGER_POST_REPLY);
            }}
    );

    final static public EnumMapper<Integer, String> outboxEventTypeStringMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OutboxEventType.LEDGER_POST_REPLY.code, EventType.POST_LEDGER_REPLY);
            }}
    );

    public static Outbox fromPostTransactionResult(PostTransactionResult result, String requestEventId, String commandId) {
        return fromPostTransactionReply(
                PostTransactionResultConverter.toProto(result),
                replyEventId(result, requestEventId),
                commandId
        );
    }

    private static Outbox fromPostTransactionReply(PostTransactionReplyPb replyPb, String replyEventId, String commandId) {
        return Outbox.create(
                OutboxEventType.LEDGER_POST_REPLY.code,
                replyEventId,
                commandId,
                OutboxStatus.PENDING,
                LedgerTopic.REPLY_WALLET,
                commandId,
                replyPb.toByteArray(),
                0,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null
        );
    }

    /**
     * Reply event ids are deterministic per reply intent:
     * success: reply:{requestEventId}:success
     * failure: reply:{requestEventId}:error:{namespace}:{errorCode}
     *
     * This is intentionally not a strict one-request-event-to-one-reply-event mapping. The same Kafka message may be
     * retried after the reply outbox is inserted but before the source message is acknowledged; in that case, the same
     * intent must reuse the same reply event id so the outbox unique key can dedupe it. A later attempt may also produce
     * a different reply intent, so failures include a stable error identity while success has one canonical id.
     *
     * Impact: one request event can produce multiple outcome-specific reply events, but each outcome is still
     * idempotent. Wallet must keep treating replies as at-least-once signals and let wallet transaction state decide
     * whether a success or failure reply can still affect the transaction.
     */
    private static String replyEventId(PostTransactionResult result, String requestEventId) {
        if (result != null && result.isSuccess()) {
            return "reply:" + requestEventId + ":success";
        }

        LedgerServiceErrorCode errorCode = result == null || result.errorCode() == null
                ? LedgerServiceErrorCode.SERVER_ERROR
                : result.errorCode();
        return "reply:" + requestEventId + ":error:" + errorCode.getNamespace() + ":" + errorCode.getMessage();
    }
}
