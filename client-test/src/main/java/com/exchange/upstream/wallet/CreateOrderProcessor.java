package com.exchange.upstream.wallet;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.wallet.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateOrderProcessor {
    @GrpcClient("wallet-client")
    private WalletServiceGrpc.WalletServiceBlockingStub blockingStub;

    public void createDirectTransfer(AssetFlow from, AssetFlow to, String idempotencyKey) {
        AtomicTransactionRequestPb req = AtomicTransactionRequestPb.newBuilder()
                .setReferenceId(idempotencyKey).setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .setIdempotencyKey(idempotencyKey)
                .addAllLines(new ArrayList<>() {{
                    add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Debit).setWalletRef(from.walletRef).setAssetCode(from.assetCode).setAmount(from.amount).build());
                    add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Credit).setWalletRef(to.walletRef).setAssetCode(to.assetCode).setAmount(to.amount).build());
                }}).build();
        AtomicTransactionReplyPb reply = blockingStub.atomicTransaction(req);
        if (reply.getError().getCode() == ErrorCodePb.ERROR_UNAVAILABLE || reply.getError().getCode() == ErrorCodePb.ERROR_INTERNAL) {
            System.out.println(reply.getError());
            throw new RuntimeException(reply.getError().toString());
        } else if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            System.out.println(reply.getError());
        }
    }

    public String createReservation(AssetFlow reservation, String idempotencyKey) {
        ReserveTransactionRequestPb req = ReserveTransactionRequestPb.newBuilder()
                .setReferenceId(idempotencyKey).setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .setIdempotencyKey(idempotencyKey)
                .addAllLines(new ArrayList<>() {{
                    add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Reserve).setWalletRef(reservation.walletRef).setAssetCode(reservation.assetCode).setAmount(reservation.amount).build());
                }}).build();
        ReserveTransactionReplyPb reply = blockingStub.reserveTransaction(req);
        if (reply.getError().getCode() == ErrorCodePb.ERROR_UNAVAILABLE || reply.getError().getCode() == ErrorCodePb.ERROR_INTERNAL) {
            System.out.println(reply.getError());
            throw new RuntimeException(reply.getError().toString());
        } else if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            System.out.println(reply.getError());
        }
        return reply.getInfosList().get(0).getReservationRef();
    }

    public void createConsume(AssetFlow[] earmark, AssetFlow[] credit, String idempotencyKey) {
        List<TransactionLinePb> lines = new ArrayList<>();
        for (AssetFlow flow : earmark) {
            lines.add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Earmark).setWalletRef(flow.walletRef).setAssetCode(flow.assetCode).setAmount(flow.amount).setReservationRef(flow.reservationRef).build());
        }
        for (AssetFlow flow : credit) {
            lines.add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Credit).setWalletRef(flow.walletRef).setAssetCode(flow.assetCode).setAmount(flow.amount).build());
        }

        ApplyReservationTransactionRequestPb req = ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId(idempotencyKey).setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .setIdempotencyKey(idempotencyKey)
                .addAllLines(lines).build();
        ApplyReservationTransactionReplyPb reply = blockingStub.applyReserveTransaction(req);
        if (reply.getError().getCode() == ErrorCodePb.ERROR_UNAVAILABLE || reply.getError().getCode() == ErrorCodePb.ERROR_INTERNAL) {
            System.out.println(reply.getError());
            throw new RuntimeException(reply.getError().toString());
        } else if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            if (!reply.getError().getMessage().equals("wallet_reservation_not_satisfied")) {
                System.out.println(reply.getError());
            }
        }
    }

    public void createRelease(AssetFlow release, String idempotencyKey) {
        ApplyReservationTransactionRequestPb req = ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId(idempotencyKey).setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .setIdempotencyKey(idempotencyKey)
                .addAllLines(new ArrayList<>() {{
                    add(TransactionLinePb.newBuilder().setOperationType(OperationTypePb.OperationTypePb_Release).setWalletRef(release.walletRef).setAssetCode(release.assetCode).setAmount(release.amount).setReservationRef(release.reservationRef).build());
                }}).build();
        ApplyReservationTransactionReplyPb reply = blockingStub.applyReserveTransaction(req);
        if (reply.getError().getCode() == ErrorCodePb.ERROR_UNAVAILABLE || reply.getError().getCode() == ErrorCodePb.ERROR_INTERNAL) {
            System.out.println(reply.getError());
            throw new RuntimeException(reply.getError().toString());
        } else if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            System.out.println(reply.getError());
        }
    }

    @AllArgsConstructor
    static public class AssetFlow {
        final public String assetCode;
        final public String walletRef;
        final public Integer amount;
        final public String reservationRef;
    }
}
