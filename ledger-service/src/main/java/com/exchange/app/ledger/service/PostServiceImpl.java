package com.exchange.app.ledger.service;


import com.exchange.app.ledger.exception.ErrorCode;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.proto.ledger.post.PostServiceGrpc;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.app.ledger.utils.ErrorHelper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PostServiceImpl extends PostServiceGrpc.PostServiceImplBase {
    final private PostLedgerProcessor postLedgerProcessor;

    @Override
    public void postTransaction(PostTransactionRequestPb request, StreamObserver<PostTransactionReplyPb> responseObserver) {
        System.out.println(request.toString());
        PostTransactionReplyPb.Builder b;
        try {
            PostTransactionReplyPb resp = postLedgerProcessor.postTransaction(request);
            b = resp.toBuilder();
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = PostTransactionReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }


}
