package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.constant.EventType;
import com.exchange.app.ledger.kafka.constant.LedgerTopic;
import com.exchange.app.ledger.po.enums.outbox.OutboxEventType;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.result.Result;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.enums.EnumMapper;
import com.exchange.common.utils.time.LongTimeHelper;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.google.protobuf.ByteString;
import org.apache.logging.log4j.util.Strings;

import java.time.LocalDateTime;
import java.util.HashMap;

public class OutboxHelper {

    static public Result<EventEnvelopePb> toEventEnvelope(Outbox outbox) {
        String eventType = outboxEventTypeStringMapper.to(outbox.getEventType());
        if (Strings.isEmpty(eventType)) {
            return Result.fail(ErrorCode.INVALID_ENUM_ERROR, "invalid outbox event type: " + outbox.getEventType());
        }
        EventEnvelopePb eventEnvelope = EventEnvelopePb.newBuilder()
                .setEventId(outbox.getEventId())
                .setCommandId(outbox.getCommandId())
                .setEventType(eventType)
                .setPayload(ByteString.copyFrom(outbox.getPayload()))
                .setOccurredAt(LongTimeHelper.now()).build();
        return Result.success(eventEnvelope);
    }

    final static public EnumMapper<Integer, OutboxEventType> outboxEventTypeMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OutboxEventType.LEDGER_POST_REPLY.code, OutboxEventType.LEDGER_POST_REPLY);
            }}
    );

    final static public EnumMapper<Integer, String> outboxEventTypeStringMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OutboxEventType.LEDGER_POST_REPLY.code, EventType.POST_LEDGER);
            }}
    );

    public static Outbox fromPostTransactionReply(PostTransactionReplyPb replyPb, String commandId) {
        return Outbox.create(
                OutboxEventType.LEDGER_POST_REPLY.code,
                IdGenerator.generateEventId(commandId),
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
}
