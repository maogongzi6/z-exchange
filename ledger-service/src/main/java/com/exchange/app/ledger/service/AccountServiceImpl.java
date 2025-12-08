package com.exchange.app.ledger.service;

import com.exchange.app.ledger.exception.ErrorCode;

import com.exchange.app.ledger.processor.account.CreateAccountProcessor;

import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.app.ledger.utils.ErrorHelper;
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
            CreateAccountReplyPb resp = createAccountProcessor.createAccount(request);
            b = resp.toBuilder();
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = CreateAccountReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
