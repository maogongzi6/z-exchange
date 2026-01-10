package com.exchange.app.ledger.service;

import com.exchange.app.ledger.result.ErrorCode;

import com.exchange.app.ledger.processor.account.CreateAccountProcessor;

import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Result;
import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {
    final private CreateAccountProcessor createAccountProcessor;

    @Override
    public void createAccount(CreateAccountRequestPb request, StreamObserver<CreateAccountReplyPb> responseObserver) {
        CreateAccountReplyPb reply;
        try {
            reply = createAccountProcessor.createAccount(request);
        } catch (Exception e) {
            log.error("uncaught exception", e);
            reply = CreateAccountReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
