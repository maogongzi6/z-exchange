package com.exchange.app.wallet.kafka.producer;

import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.app.wallet.utils.OutboxHelper;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.result.Result;
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
public class DefaultPublisher implements IPublisher {
    final private KafkaTemplate<String, byte[]> kafkaTemplate;
    public Result<Void> publish(Outbox outbox) {
        Result<EventEnvelopePb> result = OutboxHelper.toEventEnvelope(outbox);
        if (result.isFailed()) {
            log.error("outbox to event envelope failed: {}", outbox);
            return Result.failure(result);
        }
        try {
            kafkaTemplate.send(outbox.getDestination(), outbox.getPartitionKey(), result.getValue().toByteArray()).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("publish event failed, outbox: {}, event: {}, exception: {}", outbox, result.getValue(), e.toString());
            return Result.failure(WalletServiceErrorCode.PUBLISH_KAFKA_ERROR, outbox.getCommandId());
        }
        return Result.success();
    }
}
