package com.exchange.app.ledger.result;

import com.exchange.common.utils.ValidateHelper;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;

public final class PostTransactionResultConverter {
    private PostTransactionResultConverter() {
    }

    // Protobuf is a transport/event payload detail; keep conversion at service and outbox boundaries.
    public static PostTransactionReplyPb toProto(PostTransactionResult result) {
        if (result == null) {
            result = PostTransactionResult.failure(LedgerServiceErrorCode.SERVER_ERROR, "");
        }

        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        if (ValidateHelper.hasText(result.referenceId())) {
            builder.setReferenceId(result.referenceId());
        }
        if (ValidateHelper.hasText(result.ledgerTxnId())) {
            builder.setLedgerTxnId(result.ledgerTxnId());
        }

        if (result.isSuccess()) {
            builder.setError(PbErrorBuilder.success(result.detail()));
        } else {
            builder.setError(PbErrorBuilder.build(result.errorCode(), result.detail()));
        }
        return builder.build();
    }
}
