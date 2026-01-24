package com.exchange.app.ledger.kafka.producer;

import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.result.Result;
import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.proto.common.event.EventEnvelopePb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReplyWalletPublisher {
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public Result<Void> publish(Outbox outbox) {
        Result<EventEnvelopePb> result = OutboxHelper.toEventEnvelope(outbox);
        if (!result.success) {
            log.error("outbox to event envelope failed: {}", outbox);
            return Result.fail(result);
        }
        try {
            kafkaTemplate.send(outbox.getDestination(), outbox.getPartitionKey(), result.value.toByteArray()).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("publish event failed, outbox: {}, event: {}, exception: {}", outbox, result.value, e.toString());
            return Result.fail(ErrorCode.PUBLISH_KAFKA_ERROR, outbox.getCommandId());
        }
        return Result.success();
    }}
