package com.exchange.app.wallet.kafka.consumer;

import com.exchange.app.wallet.kafka.constant.WalletTopic;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.processor.transaction.step.AfterPostLedgerProcessor;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.exception.AbnormalProtoDataException;
import com.exchange.common.exception.RetriableException;
import com.exchange.common.result.Result;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
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
public class DefaultListener {
    private final AfterPostLedgerProcessor afterPostLedgerProcessor;

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
            throw new AbnormalProtoDataException(WalletServiceErrorCode.SERIALIZE_ERROR, "Error parsing envelope", e);
        }
    }

    private void handleMessage(EventEnvelopePb envelope) throws InvalidProtocolBufferException {
        log.info("handleMessage: {}", envelope);
        switch (envelope.getEventType()) {
            case "POST_LEDGER_REPLY":
                handlePostLedgerReply(envelope);
                break;
            default:
                throw new AbnormalProtoDataException(WalletServiceErrorCode.INVALID_ENUM_ERROR, "invalid event type" + envelope.getEventType());
        }
    }

    private void handlePostLedgerReply(EventEnvelopePb envelope) throws InvalidProtocolBufferException{
        var reply = PostTransactionReplyPb.parseFrom(envelope.getPayload());
        log.info("handlePostLedgerReply: {}", reply);
        Result<WalletTransaction> result = afterPostLedgerProcessor.afterPostLedger(reply.getReferenceId());
        // TODO retry base on error
        if (result.isFailed()) {
            log.error("wait for retry: {}", reply.getError());
            throw new RetriableException(WalletServiceErrorCode.SERVER_ERROR, "internal error, wait for retry," + reply.getError());
        }
    }
}
