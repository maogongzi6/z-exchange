package com.exchange.app.ledger.kafka.consumer;

import com.exchange.app.ledger.result.LedgerBoundaryErrorMapper;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.result.PostTransactionResult;
import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.common.kafka.listener.handler.FailureReplyFactory;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.proto.common.event.EventEnvelopePb;
import org.springframework.stereotype.Component;

@Component
public class LedgerFailureReplyFactory implements FailureReplyFactory {
    @Override
    public Outbox createFailureReply(EventEnvelopePb envelope, ListenerFailure failure) {
        LedgerServiceErrorCode ledgerError = LedgerBoundaryErrorMapper.toLedgerErrorCode(failure.errorCode());
        PostTransactionResult result = PostTransactionResult.failure(ledgerError, failure.detail());
        return OutboxHelper.fromPostTransactionResult(result, envelope.getEventId(), envelope.getCommandId());
    }
}
