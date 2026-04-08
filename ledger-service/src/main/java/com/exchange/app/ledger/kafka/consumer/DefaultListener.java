package com.exchange.app.ledger.kafka.consumer;

import com.exchange.app.ledger.constant.EventType;
import com.exchange.app.ledger.cronjob.outbox.constant.OutboxConstant;
import com.exchange.app.ledger.exception.AbnormalProtoDataException;
import com.exchange.app.ledger.exception.RetriableException;
import com.exchange.app.ledger.kafka.constant.LedgerTopic;
import com.exchange.app.ledger.kafka.producer.DefaultPublisher;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.result.Result;
import com.exchange.common.utils.time.LocalDateTimeHelper;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.common.event.EventEnvelopePb;
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
    private final PostLedgerProcessor postLedgerProcessor;
    private final OutboxRepository outboxManager;
    private final DefaultPublisher replyWalletPublisher;

    @KafkaListener(
            topics = LedgerTopic.WALLET_POST,
            groupId = "wallet-post",
            containerFactory = "concurrentCommandKafkaListenerContainerFactory"
    )
    public void onMessage(byte[] envelopeBytes, Acknowledgment ack) {
        log.info("onMessage");
        try {
            var envelope = EventEnvelopePb.parseFrom(envelopeBytes);
            Outbox replyOutbox = handleMessage(envelope);
            if (outboxManager.insertWithClaim(replyOutbox, LocalDateTimeHelper.nowAfterMs(OutboxConstant.BASE_ATTEMPT_INTERVAL_MS))
                    != 1) {
                log.error("duplicated outbox, {}", replyOutbox);
                throw new RetriableException(LedgerServiceErrorCode.LEDGER_OUTBOX_DUPLICATED, "insert outbox failed, outbox=" + replyOutbox);
            }
            // ack first, then try to publish immediately
            ack.acknowledge();
            tryToPublishReply(replyOutbox);
        } catch (InvalidProtocolBufferException e) {
            log.error("Error parsing envelope", e);
            throw new AbnormalProtoDataException(LedgerServiceErrorCode.SERIALIZE_ERROR, "Error parsing envelope", e);
        }
    }

    private Outbox handleMessage(EventEnvelopePb envelope) throws InvalidProtocolBufferException {
        log.info("handleMessage: {}", envelope);
        Outbox replyOutbox;
        switch (envelope.getEventType()) {
            case EventType.POST_LEDGER:
                replyOutbox = handlePostLedger(envelope);
                break;
            default:
                throw new AbnormalProtoDataException(LedgerServiceErrorCode.INVALID_ENUM_ERROR, "invalid event type" + envelope.getEventType());
        }
        return replyOutbox;
    }

    private Outbox handlePostLedger(EventEnvelopePb envelope) throws InvalidProtocolBufferException {
        var req = PostTransactionRequestPb.parseFrom(envelope.getPayload());
        log.info("handlePostLedger: {}", req);
        var reply = postLedgerProcessor.postTransaction(req);
        // TODO retriable error, redesign the error logic, how to specify retriable error?
        if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            if (reply.getError().getCode() != ErrorCodePb.ERROR_INTERNAL) {
                log.error("internal error, wait for retry: {}", reply.getError());
                throw new RetriableException(LedgerServiceErrorCode.SERVER_ERROR, "internal error, wait for retry," + reply.getError());
            } else {
                log.error("non retriable, queue dlq: {}", reply.getError());
                // TODO change exception type
                throw new AbnormalProtoDataException(LedgerServiceErrorCode.SERVER_ERROR, "non retriable, queue dlq");
            }
        }
        return OutboxHelper.fromPostTransactionReply(reply, envelope.getCommandId());
    }

    private void tryToPublishReply(Outbox replyOutbox) {
        Result<Void> result = replyWalletPublisher.publish(replyOutbox);
        if (result.isSuccess()) {
            if (outboxManager.updateStatusToFinalize(replyOutbox, OutboxStatus.SENT, replyOutbox.getLastAttemptAt()) != 1) {
                // do not fail, ack as normal, wait cronjob to retry outbox
                log.error("finalize outbox failed, {}", replyOutbox);
            }
        } else {
            // do not fail, ack as normal, wait cronjob to retry outbox
            log.error("publish reply failed, outbox={}, result={}", replyOutbox, result);
        }
    }
}
