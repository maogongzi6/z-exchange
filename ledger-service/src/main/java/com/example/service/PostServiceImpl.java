package com.example.service;


import com.example.exception.ErrorCode;
import com.example.pojo.ProtobufBeanHelper;
import com.example.pojo.ledger.post.PostTransactionReply;
import com.example.pojo.ledger.post.PostTransactionRequest;
import com.example.processor.post.PostLedgerProcessor;
import com.example.proto.ledger.post.PostServiceGrpc;
import com.example.proto.ledger.post.PostTransactionReplyPb;
import com.example.proto.ledger.post.PostTransactionRequestPb;
import com.example.utils.ErrorHelper;
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
            PostTransactionRequest req = ProtobufBeanHelper.toPojoBean(PostTransactionRequest.class, request);
            PostTransactionReply resp = postLedgerProcessor.postTransaction(req);
            b = PostTransactionReplyPb.newBuilder();
            ProtobufBeanHelper.toProtoBean(b, resp);
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = PostTransactionReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }


}
