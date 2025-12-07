package com.example.service;

import com.example.exception.ErrorCode;
import com.example.pojo.ProtobufBeanHelper;
import com.example.pojo.ledger.account.CreateAccountReply;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.processor.account.CreateAccountProcessor;
import com.example.proto.ledger.account.AccountServiceGrpc;
import com.example.proto.ledger.account.CreateAccountReplyPb;
import com.example.proto.ledger.account.CreateAccountRequestPb;
import com.example.utils.ErrorHelper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {
    final private CreateAccountProcessor createAccountProcessor;

    @Override
    public void createAccount(CreateAccountRequestPb request, StreamObserver<CreateAccountReplyPb> responseObserver) {
        System.out.println(request.toString());
        CreateAccountReplyPb.Builder b;
        try {
            CreateAccountRequest req = ProtobufBeanHelper.toPojoBean(CreateAccountRequest.class, request);
            CreateAccountReply resp = createAccountProcessor.createAccount(req);
            b = CreateAccountReplyPb.newBuilder();
            ProtobufBeanHelper.toProtoBean(b, resp);
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = CreateAccountReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
