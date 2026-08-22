package com.exchange.common.outbox.retry;

import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.kafka.producer.PublishSource;
import com.exchange.common.outbox.config.OutboxProperties;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRetryHandlerTest {
    @Mock
    private OutboxRepository repository;
    @Mock
    private DbTxnExecutor dbTxnExecutor;
    @Mock
    private IPublisher publisher;

    @SuppressWarnings("unchecked")
    @Test
    void retryWorkerMarksPublisherAttemptAsRetrySource() {
        // Retry-source tagging separates scheduled recovery from latency-sensitive immediate sends.
        OutboxProperties properties = new OutboxProperties();
        properties.setPageLimit(10);
        properties.setMaxRetries(5);
        properties.setAttemptIntervalSec(5);
        Outbox outbox = Outbox.create(
                1,
                "event-1",
                "command-1",
                OutboxStatus.PENDING,
                "topic-1",
                "command-1",
                new byte[]{1},
                1,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null
        );
        outbox.setId(1L);

        when(dbTxnExecutor.executeWithDefault(any())).thenAnswer(invocation ->
                ((Supplier<Result<List<Outbox>>>) invocation.getArgument(0)).get());
        when(repository.selectForClaimSkipLock(any(LocalDateTime.class), eq(5), anyLong(), eq(10)))
                .thenReturn(List.of(outbox));
        when(repository.batchClaim(anyList(), any(LocalDateTime.class))).thenReturn(1);
        when(publisher.publishAsync(outbox, PublishSource.RETRY))
                .thenReturn(CompletableFuture.completedFuture(Result.success()));
        when(repository.batchUpdateStatusToFinalize(
                anyList(),
                eq(OutboxStatus.PENDING),
                eq(OutboxStatus.SENT),
                any(LocalDateTime.class)
        )).thenReturn(1);

        new OutboxRetryHandler(properties, repository, dbTxnExecutor, publisher).retry();

        verify(publisher).publishAsync(outbox, PublishSource.RETRY);
    }
}
