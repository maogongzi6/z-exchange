package com.exchange.stress.ledger;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.GetTxnByIdRequestPb;
import com.exchange.proto.ledger.post.GetTxnByRefIdRequestPb;
import com.exchange.proto.ledger.post.GetTxnReplyPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostServiceGrpc;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.grpc.GrpcProtocolBuilder;
import io.gatling.javaapi.grpc.GrpcServerConfigurationBuilder;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.grpc.GrpcDsl.grpc;
import static io.gatling.javaapi.grpc.GrpcDsl.response;
import static io.gatling.javaapi.grpc.GrpcDsl.statusCode;
import static io.grpc.Status.Code.OK;

public final class LedgerPostServiceSmokeSimulation extends Simulation {
    private static final String REFERENCE_ID = "referenceId";
    private static final String LEDGER_TXN_ID = "ledgerTxnId";

    public LedgerPostServiceSmokeSimulation() {
        // Fixture creation is deliberately outside the Gatling scenario so it does not
        // affect the measured PostService request count or latency.
        LedgerFixtureBootstrap.ensureAccountExists();

        GrpcServerConfigurationBuilder ledgerServer = grpc
                .serverConfiguration("ledger")
                .forAddress(LedgerSmokeConfig.HOST, LedgerSmokeConfig.PORT)
                .usePlaintext();
        GrpcProtocolBuilder grpcProtocol = grpc.serverConfigurations(ledgerServer);

        ScenarioBuilder smoke = scenario("Ledger PostService smoke")
                .exec(session -> session.set(
                        REFERENCE_ID,
                        "gatling-smoke-" + UUID.randomUUID()
                ))
                .exec(
                        grpc("postTransaction")
                                .unary(PostServiceGrpc.getPostTransactionMethod())
                                .send(session -> postRequest(session.getString(REFERENCE_ID)))
                                .deadlineAfter(Duration.ofSeconds(LedgerSmokeConfig.DEADLINE_SECONDS))
                                .check(
                                        statusCode().is(OK),
                                        response((PostTransactionReplyPb reply) -> reply.getError().getCode())
                                                .is(ErrorCodePb.ERROR_OK),
                                        response(PostTransactionReplyPb::getLedgerTxnId)
                                                .saveAs(LEDGER_TXN_ID)
                                )
                )
                .exitHereIfFailed()
                .exec(
                        grpc("getTxnByRefId")
                                .unary(PostServiceGrpc.getGetTxnByRefIdMethod())
                                .send(session -> GetTxnByRefIdRequestPb.newBuilder()
                                        .setReferenceId(session.getString(REFERENCE_ID))
                                        .setIncludeEntries(true)
                                        .build())
                                .deadlineAfter(Duration.ofSeconds(LedgerSmokeConfig.DEADLINE_SECONDS))
                                .check(
                                        statusCode().is(OK),
                                        response((GetTxnReplyPb reply) -> reply.getError().getCode())
                                                .is(ErrorCodePb.ERROR_OK),
                                        response((GetTxnReplyPb reply) -> reply.getTransaction().getReferenceId())
                                                .is(session -> session.getString(REFERENCE_ID)),
                                        response(GetTxnReplyPb::getEntriesCount).is(2)
                                )
                )
                .exitHereIfFailed()
                .exec(
                        grpc("getTxnById")
                                .unary(PostServiceGrpc.getGetTxnByIdMethod())
                                .send(session -> GetTxnByIdRequestPb.newBuilder()
                                        .setLedgerTxnId(session.getString(LEDGER_TXN_ID))
                                        .setIncludeEntries(true)
                                        .build())
                                .deadlineAfter(Duration.ofSeconds(LedgerSmokeConfig.DEADLINE_SECONDS))
                                .check(
                                        statusCode().is(OK),
                                        response((GetTxnReplyPb reply) -> reply.getError().getCode())
                                                .is(ErrorCodePb.ERROR_OK),
                                        response((GetTxnReplyPb reply) -> reply.getTransaction().getLedgerTxnId())
                                                .is(session -> session.getString(LEDGER_TXN_ID)),
                                        response(GetTxnReplyPb::getEntriesCount).is(2)
                                )
                );

        setUp(smoke.injectOpen(atOnceUsers(1)))
                .protocols(grpcProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }

    private static PostTransactionRequestPb postRequest(String referenceId) {
        return PostTransactionRequestPb.newBuilder()
                .setReferenceId(referenceId)
                .setDescription("Gatling PostService smoke transaction")
                .addEntries(entry(LedgerDirectionPb.LedgerDirection_Debit))
                .addEntries(entry(LedgerDirectionPb.LedgerDirection_Credit))
                .build();
    }

    private static LedgerEntryPb entry(LedgerDirectionPb direction) {
        return LedgerEntryPb.newBuilder()
                .setAccountRef(LedgerSmokeConfig.ACCOUNT_REF)
                .setDirection(direction)
                .setAmount(LedgerSmokeConfig.AMOUNT)
                .setAssetId(LedgerSmokeConfig.ASSET_ID)
                .build();
    }
}
