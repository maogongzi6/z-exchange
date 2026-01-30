package com.exchange.common.kafka.producer;

import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.utils.result.Result;

public interface IPublisher {
    Result<Void> publish(Outbox outbox);
}
