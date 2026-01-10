package com.exchange.app.ledger.service;


import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.post.PostServiceGrpc;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
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

    @Override
    public void postTransaction(PostTransactionRequestPb request, StreamObserver<PostTransactionReplyPb> responseObserver) {
        PostTransactionReplyPb reply;
        try {
            reply = postLedgerProcessor.postTransaction(request);
        } catch (Exception e) {
            log.error("uncaught exception", e);
            reply = PostTransactionReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }


}
