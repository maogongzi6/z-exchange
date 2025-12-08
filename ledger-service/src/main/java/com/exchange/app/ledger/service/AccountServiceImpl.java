package com.exchange.app.ledger.service;

import com.exchange.app.ledger.result.ErrorCode;

import com.exchange.app.ledger.processor.account.CreateAccountProcessor;

import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Result;
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
        CreateAccountReplyPb reply;
        try {
            reply = createAccountProcessor.createAccount(request);
        } catch (Exception e) {
            reply = CreateAccountReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
