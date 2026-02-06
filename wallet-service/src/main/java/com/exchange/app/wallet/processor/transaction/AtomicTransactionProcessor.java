package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.*;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AtomicTransactionProcessor {
    private final WalletTransactionRepository walletTransactionManager;

    private final TransactionProcessor transactionProcessor;

    public AtomicTransactionReplyPb executeAtomic(AtomicTransactionRequestPb request) {

        Result<TransactionProcessor.TransactionInfo> result = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.ATOMIC, true, request.getLinesList());

//        } else if (walletTxn.getTxnStatus() == TransactionStatus.PENDING) {
//            txnInfoResult = transactionProcessor.getTxnInfo(walletTxn, requestInfo);
//        } else {
//            return replySuccess(walletTxn, "txn already executed");
//        }
        if (result.isFailed()) {
            return replyError(result);
        }
//        TransactionProcessor.TransactionInfo transactionInfo = txnInfoResult.value;
//        Result<Void> result = transactionProcessor.postLedger(transactionInfo);
//        if (!result.success) {
//            return replyError(result);
//        }
//        Result<WalletTransaction> txnResult = transactionProcessor.afterPostingLedger(requestInfo, transactionInfo);
//        if (!txnResult.success) {
//            return replyError(txnResult);
//        }

        return replySuccess(result.value.walletTxn, "success");
    }

    private AtomicTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        return AtomicTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumPbMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private AtomicTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        AtomicTransactionReplyPb.Builder builder = AtomicTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private AtomicTransactionReplyPb replyError(Result<?> result) {
        return replyError(Results.getErrorCode(result), result.errorDetail);
    }
}
