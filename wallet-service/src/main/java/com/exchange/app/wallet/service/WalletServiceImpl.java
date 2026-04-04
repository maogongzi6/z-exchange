package com.exchange.app.wallet.service;

import com.exchange.app.wallet.processor.transaction.ApplyReservationTransactionProcessor;
import com.exchange.app.wallet.processor.transaction.AtomicTransactionProcessor;
import com.exchange.app.wallet.processor.CreateWalletProcessor;
import com.exchange.app.wallet.processor.balance.GetBalanceSnapshotProcessor;
import com.exchange.app.wallet.processor.transaction.ReserveTransactionProcessor;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.PbConverter;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.wallet.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {
    final private CreateWalletProcessor createWalletProcessor;
    final private AtomicTransactionProcessor atomicTransactionProcessor;
    final private ReserveTransactionProcessor reserveTransactionProcessor;
    private final ApplyReservationTransactionProcessor applyReservationTransactionProcessor;
    private final GetBalanceSnapshotProcessor getBalanceSnapshotProcessor;

    @Override
    public void createWallet(CreateWalletRequestPb request, StreamObserver<CreateWalletReplyPb> responseObserver) {
        CreateWalletReplyPb reply;
        try {
            reply = createWalletProcessor.createWallet(request);
        } catch (Exception e) {
            log.error("uncaught exception", e);
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
            log.error("uncaught exception", e);
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
            log.error("uncaught exception", e);
            reply = ReserveTransactionReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void applyReserveTransaction(ApplyReservationTransactionRequestPb request, StreamObserver<ApplyReservationTransactionReplyPb> responseObserver) {
        ApplyReservationTransactionReplyPb reply;
        try {
            reply = applyReservationTransactionProcessor.apply(request);
        } catch (Exception e) {
            log.error("uncaught exception", e);
            reply = ApplyReservationTransactionReplyPb.newBuilder().setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR)).build();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void getSnapshotByWalletId(GetSnapshotByIdRequestPb request, StreamObserver<GetSnapshotReplyPb> responseObserver) {
        handleSnapshotRequest(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, request.getWalletId(), responseObserver);
    }

    @Override
    public void getSnapshotByRefId(GetSnapshotByRefIdRequestPb request, StreamObserver<GetSnapshotReplyPb> responseObserver) {
        handleSnapshotRequest(GetBalanceSnapshotProcessor.LookupType.REF_ID, request.getReferenceId(), responseObserver);
    }

    private void handleSnapshotRequest(GetBalanceSnapshotProcessor.LookupType lookupType,
                                       String lookupValue,
                                       StreamObserver<GetSnapshotReplyPb> responseObserver) {
        try {
            Result<com.exchange.app.wallet.po.wallet.BalanceSnapshot> result = getBalanceSnapshotProcessor.getBalanceSnapshot(lookupType, lookupValue);
            if (result.success()) {
                responseObserver.onNext(GetSnapshotReplyPb.newBuilder()
                        .setTransaction(PbConverter.convertToBalanceSnapshotPb(result.value()))
                        .build());
            } else {
                responseObserver.onNext(GetSnapshotReplyPb.newBuilder()
                        .setError(PbErrorBuilder.build(Results.getErrorCode(result), result.errorDetail()))
                        .build());
            }
        } catch (Exception e) {
            log.error("error processing balance snapshot request", e);
            responseObserver.onNext(GetSnapshotReplyPb.newBuilder()
                    .setError(PbErrorBuilder.build(ErrorCode.SERVER_ERROR))
                    .build());
        }
        responseObserver.onCompleted();
    }
}
