package com.exchange.app.wallet.service;

import com.exchange.app.wallet.processor.transaction.AtomicTransactionProcessor;
import com.exchange.app.wallet.processor.CreateWalletProcessor;
import com.exchange.app.wallet.processor.transaction.ReserveTransactionProcessor;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.proto.wallet.wallet.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {
    final private CreateWalletProcessor createWalletProcessor;
    final private AtomicTransactionProcessor atomicTransactionProcessor;
    final private ReserveTransactionProcessor reserveTransactionProcessor;

    @Override
    public void createWallet(CreateWalletRequestPb request, StreamObserver<CreateWalletReplyPb> responseObserver) {
        CreateWalletReplyPb reply;
        try {
            reply = createWalletProcessor.createWallet(request);
        } catch (Exception e) {
            reply = CreateWalletReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    // at most one transaction line for one wallet id
    @Override
    public void atomicTransaction(AtomicTransactionRequestPb request, StreamObserver<AtomicTransactionReplyPb> responseObserver) {
        AtomicTransactionReplyPb reply;
        try {
            reply = atomicTransactionProcessor.executeAtomic(request);
        } catch (Exception e) {
            reply = AtomicTransactionReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveTransaction(ReserveTransactionRequestPb request, StreamObserver<ReserveTransactionReplyPb> responseObserver) {
        ReserveTransactionReplyPb reply;
        try {
            reply = reserveTransactionProcessor.reserve(request);
        } catch (Exception e) {
            reply = ReserveTransactionReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
