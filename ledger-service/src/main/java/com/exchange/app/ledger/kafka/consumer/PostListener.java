package com.exchange.app.ledger.kafka.consumer;

import com.exchange.app.ledger.constant.EventType;
import com.exchange.app.ledger.cronjob.constant.OutboxConfig;
import com.exchange.app.ledger.exception.AbnormalProtoDataException;
import com.exchange.app.ledger.exception.RetriableException;
import com.exchange.app.ledger.kafka.constant.LedgerTopic;
import com.exchange.app.ledger.kafka.producer.ReplyWalletPublisher;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.result.Result;
import com.exchange.app.ledger.utils.IdGenerator;
import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.outbox.dao.manager.OutboxManager;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
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

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PostListener {
    private final PostLedgerProcessor postLedgerProcessor;
    private final OutboxManager outboxManager;
    private final ReplyWalletPublisher replyWalletPublisher;

    @KafkaListener(
            topics = LedgerTopic.WALLET_POST,
            groupId = "wallet-post",
            containerFactory = "concurrentCommandKafkaListenerContainerFactory"
    )
    public void onMessage(byte[] envelopeBytes, Acknowledgment ack) {
        if (true) {throw new RetriableException(ErrorCode.UNKNOWN_ERROR, "MOCK");}

        try {
            var envelope = EventEnvelopePb.parseFrom(envelopeBytes);
            Outbox replyOutbox = handleMessage(envelope);
            if (outboxManager.insertWithClaim(replyOutbox, LocalDateTimeHelper.nowAfterMs(OutboxConfig.BASE_ATTEMPT_INTERVAL_MS))
                    != 1) {
                log.error("duplicated outbox, {}", replyOutbox);
                throw new RetriableException(ErrorCode.LEDGER_OUTBOX_DUPLICATED, "insert outbox failed, outbox=" + replyOutbox);
            }
            // ack first, then try to publish immediately
            ack.acknowledge();
            tryToPublishReply(replyOutbox);
        } catch (InvalidProtocolBufferException e) {
            log.error("Error parsing envelope", e);
            throw new AbnormalProtoDataException(ErrorCode.SERIALIZE_ERROR, "Error parsing envelope", e);
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
                throw new AbnormalProtoDataException(ErrorCode.INVALID_ENUM_ERROR, "invalid event type" + envelope.getEventType());
        }
        return replyOutbox;
    }

    private Outbox handlePostLedger(EventEnvelopePb envelope) throws InvalidProtocolBufferException {
        var req = PostTransactionRequestPb.parseFrom(envelope.getPayload());
        log.info("handlePostLedger: {}", req);
        var reply = postLedgerProcessor.postTransaction(req);
        // TODO retriable error, redesign the error logic, how to specify retriable error?
        if (reply.hasError() && reply.getError().getCode() == ErrorCodePb.ERROR_INTERNAL) {
            log.error("internal error, wait for retry: {}", reply.getError());
            throw new RetriableException(ErrorCode.SERVER_ERROR, "internal error, wait for retry," + reply.getError());
        }
        return OutboxHelper.fromPostTransactionReply(reply, envelope.getCommandId());
    }

    private void tryToPublishReply(Outbox replyOutbox) {
        Result<Void> result = replyWalletPublisher.publish(replyOutbox);
        if (result.success) {
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
