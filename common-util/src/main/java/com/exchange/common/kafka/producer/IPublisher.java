package com.exchange.common.kafka.producer;

import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.result.Result;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes an already-durable outbox event without changing its database status.
 *
 * <p>The returned future completes with success only after the broker acknowledges the send.
 * Callers own the outbox lifecycle and may finalize the row as sent only after that success.
 * A failed result or exceptional completion must leave the row retryable.</p>
 */
public interface IPublisher {
    /**
     * Starts a non-blocking publish attempt for one outbox event.
     */
    CompletableFuture<Result<Void>> publishAsync(Outbox outbox);

    /**
     * Blocking adapter for call sites that cannot use the asynchronous contract.
     */
    default Result<Void> publish(Outbox outbox) {
        return publishAsync(outbox).join();
    }
}
