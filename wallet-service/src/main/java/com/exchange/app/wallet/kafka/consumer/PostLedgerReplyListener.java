package com.exchange.app.wallet.kafka.consumer;

import com.exchange.app.wallet.exception.AbnormalProtoDataException;
import com.exchange.app.wallet.exception.RetriableException;
import com.exchange.app.wallet.kafka.constant.WalletTopic;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.processor.transaction.TransactionProcessor;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.common.outbox.dao.manager.OutboxManager;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PostLedgerReplyListener {
    private final TransactionProcessor transactionProcessor;

    @KafkaListener(
            topics = WalletTopic.LEDGER_REPLY,
            groupId = "ledger_reply",
            containerFactory = "concurrentCommandKafkaListenerContainerFactory"
    )
    public void onMessage(byte[] envelopeBytes, Acknowledgment ack) {
        try {
            var envelope = EventEnvelopePb.parseFrom(envelopeBytes);
            handleMessage(envelope);
            ack.acknowledge();
        } catch (InvalidProtocolBufferException e) {
            log.error("Error parsing envelope", e);
            throw new AbnormalProtoDataException(ErrorCode.SERIALIZE_ERROR, "Error parsing envelope", e);
        }
    }

    private void handleMessage(EventEnvelopePb envelope) throws InvalidProtocolBufferException {
        log.info("handleMessage: {}", envelope);
        switch (envelope.getEventType()) {
            case "POST_LEDGER_REPLY":
                handlePostLedgerReply(envelope);
                break;
            default:
                throw new AbnormalProtoDataException(ErrorCode.INVALID_ENUM_ERROR, "invalid event type" + envelope.getEventType());
        }
    }

    private void handlePostLedgerReply(EventEnvelopePb envelope) throws InvalidProtocolBufferException{
        var reply = PostTransactionReplyPb.parseFrom(envelope.getPayload());
        log.info("handlePostLedgerReply: {}", reply);
        Result<WalletTransaction> result = transactionProcessor.afterPostLedger(reply.getReferenceId());
        // TODO retry base on error
        if (result.isFailed()) {
            log.error("wait for retry: {}", reply.getError());
            throw new RetriableException(ErrorCode.SERVER_ERROR, "internal error, wait for retry," + reply.getError());
        }
    }
}
