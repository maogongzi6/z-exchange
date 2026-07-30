package com.exchange.stress.ledger;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.proto.ledger.common.AccountCategoryPb;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.common.NormalSidePb;
import com.exchange.proto.ledger.common.OwnerTypePb;
import com.exchange.proto.ledger.common.ServiceIdPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostServiceGrpc;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

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

    static LedgerTransactionFixture createLedgerTransaction() {
        ensureAccountExists();
        String referenceId = "gatling-http-smoke-" + UUID.randomUUID();
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(LedgerSmokeConfig.HOST, LedgerSmokeConfig.PORT)
                .usePlaintext()
                .build();
        try {
            PostTransactionRequestPb request = PostTransactionRequestPb.newBuilder()
                    .setReferenceId(referenceId)
                    .setDescription("Gatling HTTP query smoke fixture")
                    .addEntries(entry(LedgerDirectionPb.LedgerDirection_Debit))
                    .addEntries(entry(LedgerDirectionPb.LedgerDirection_Credit))
                    .build();
            PostTransactionReplyPb reply = PostServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(LedgerSmokeConfig.DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .postTransaction(request);
            if (reply.getError().getCode() != ErrorCodePb.ERROR_OK || reply.getLedgerTxnId().isBlank()) {
                throw new IllegalStateException(
                        "Unable to prepare HTTP smoke-test transaction: " + reply
                );
            }
            return new LedgerTransactionFixture(referenceId, reply.getLedgerTxnId());
        } finally {
            shutdown(channel);
        }
    }

    private static LedgerEntryPb entry(LedgerDirectionPb direction) {
        return LedgerEntryPb.newBuilder()
                .setAccountRef(LedgerSmokeConfig.ACCOUNT_REF)
                .setDirection(direction)
                .setAmount(LedgerSmokeConfig.AMOUNT)
                .setAssetId(LedgerSmokeConfig.ASSET_ID)
                .build();
    }

    private static void shutdown(ManagedChannel channel) {
        channel.shutdownNow();
        try {
            channel.awaitTermination(LedgerSmokeConfig.DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record LedgerTransactionFixture(String referenceId, String ledgerTxnId) {
    }
}
