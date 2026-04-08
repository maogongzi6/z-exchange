package com.exchange.app.ledger.service;


import com.exchange.app.ledger.processor.post.GetLedgerTxnProcessor;
import com.exchange.app.ledger.result.LedgerBoundaryErrorMapper;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.utils.PbConverter;
import com.exchange.common.result.Result;
import com.exchange.proto.ledger.post.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PostServiceImpl extends PostServiceGrpc.PostServiceImplBase {
    final private PostLedgerProcessor postLedgerProcessor;
    private final GetLedgerTxnProcessor getLedgerTxnProcessor;

    @Override
    public void getTxnById(GetTxnByIdRequestPb request, StreamObserver<GetTxnReplyPb> responseObserver) {
        handleTransactionRequest(GetLedgerTxnProcessor.LookupType.TXN_ID, request.getLedgerTxnId(),
                request.getIncludeEntries(), responseObserver);
    }

    @Override
    public void getTxnByRefId(GetTxnByRefIdRequestPb request, StreamObserver<GetTxnReplyPb> responseObserver) {
        handleTransactionRequest(GetLedgerTxnProcessor.LookupType.REF_ID, request.getReferenceId(),
                request.getIncludeEntries(), responseObserver);
    }

    private void handleTransactionRequest(GetLedgerTxnProcessor.LookupType lookupType, String lookupValue,
                                          boolean includeEntries, StreamObserver<GetTxnReplyPb> responseObserver) {
        try {
            Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(lookupType, lookupValue, includeEntries);
            if (result.isSuccess()) {
                GetLedgerTxnProcessor.LedgerTxnInfo info = result.getValue();
                GetTxnReplyPb.Builder replyBuilder = GetTxnReplyPb.newBuilder()
                        .setTransaction(PbConverter.convertToLedgerTransactionPb(info.txn));
                if (includeEntries) {
                    replyBuilder.addAllEntries(PbConverter.convertToLedgerEntryPbs(info.entries));
                }
                responseObserver.onNext(replyBuilder.build());
            } else {
                LedgerServiceErrorCode errorCode = LedgerBoundaryErrorMapper.toLedgerErrorCode(result);
                responseObserver.onNext(GetTxnReplyPb.newBuilder()
                        .setError(PbErrorBuilder.build(errorCode, result.getDetail()))
                        .build());
            }
        } catch (Exception e) {
            log.error("Error processing transaction request", e);
            responseObserver.onNext(GetTxnReplyPb.newBuilder()
                    .setError(PbErrorBuilder.build(LedgerServiceErrorCode.SERVER_ERROR))
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void postTransaction(PostTransactionRequestPb request, StreamObserver<PostTransactionReplyPb> responseObserver) {
        PostTransactionReplyPb reply;
        try {
            reply = postLedgerProcessor.postTransaction(request);
        } catch (Exception e) {
            log.error("uncaught exception", e);
            reply = PostTransactionReplyPb.newBuilder()
                    .setError(PbErrorBuilder.build(LedgerServiceErrorCode.SERVER_ERROR))
                    .build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    
}
