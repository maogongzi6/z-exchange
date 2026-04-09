package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.constant.EventType;
import com.exchange.app.wallet.kafka.constant.WalletTopic;
import com.exchange.app.wallet.po.enums.outbox.OutboxEventType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.result.Result;
import com.exchange.common.utils.enums.EnumMapper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.utils.time.LongTimeHelper;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.google.protobuf.ByteString;
import org.apache.logging.log4j.util.Strings;

import java.time.LocalDateTime;
import java.util.HashMap;

public class OutboxHelper {

    static public Result<EventEnvelopePb> toEventEnvelope(Outbox outbox) {
        String eventType = outboxEventTypeStringMapper.to(outbox.getEventType());
        if (Strings.isEmpty(eventType)) {
            return Result.failure(WalletServiceErrorCode.INVALID_ENUM_ERROR, "invalid outbox event type: " + outbox.getEventType());
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
                put(OutboxEventType.LEDGER_POST.code, OutboxEventType.LEDGER_POST);
            }}
    );

    final static public EnumMapper<Integer, String> outboxEventTypeStringMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OutboxEventType.LEDGER_POST.code, EventType.POST_LEDGER);
            }}
    );

    public static Outbox fromPostTransactionRequest(PostTransactionRequestPb requestPb, WalletTransaction txn) {
        return Outbox.create(
                OutboxEventType.LEDGER_POST.code,
                IdGenerator.generateEventId(txn.getTxnId()),
                txn.getTxnId(),
                OutboxStatus.PENDING,
                WalletTopic.POST_LEDGER,
                txn.getTxnId(),
                requestPb.toByteArray(),
                0,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null
        );
    }
}
