package com.exchange.stress.ledger;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.proto.ledger.common.AccountCategoryPb;
import com.exchange.proto.ledger.common.NormalSidePb;
import com.exchange.proto.ledger.common.OwnerTypePb;
import com.exchange.proto.ledger.common.ServiceIdPb;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

final class LedgerFixtureBootstrap {
    private LedgerFixtureBootstrap() {
    }

    static void ensureAccountExists() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(LedgerSmokeConfig.HOST, LedgerSmokeConfig.PORT)
                .usePlaintext()
                .build();
        try {
            CreateAccountRequestPb request = CreateAccountRequestPb.newBuilder()
                    .setReferenceId(LedgerSmokeConfig.ACCOUNT_REF)
                    .setServiceId(ServiceIdPb.ServiceIdPb_Wallet)
                    .setAssetId(LedgerSmokeConfig.ASSET_ID)
                    .setCategory(AccountCategoryPb.AccountCategory_Asset)
                    .setNormalSide(NormalSidePb.NormalSidePb_Debit)
                    .setOwnerType(OwnerTypePb.OwnerTypePb_User)
                    .setOwnerId("gatling-smoke-owner")
                    .build();

            CreateAccountReplyPb reply = AccountServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(LedgerSmokeConfig.DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .createAccount(request);
            if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
                throw new IllegalStateException(
                        "Unable to prepare smoke-test account: " + reply.getError()
                );
            }
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(LedgerSmokeConfig.DEADLINE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
