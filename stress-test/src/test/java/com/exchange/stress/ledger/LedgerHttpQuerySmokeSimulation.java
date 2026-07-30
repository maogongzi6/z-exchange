package com.exchange.stress.ledger;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class LedgerHttpQuerySmokeSimulation extends Simulation {
    public LedgerHttpQuerySmokeSimulation() {
        // The fixture is created through internal gRPC before measurement. The scenario therefore
        // verifies only the public HTTP read contract and does not mix setup writes into latency.
        LedgerFixtureBootstrap.LedgerTransactionFixture fixture =
                LedgerFixtureBootstrap.createLedgerTransaction();

        HttpProtocolBuilder httpProtocol = http
                .baseUrl("http://" + LedgerSmokeConfig.HTTP_HOST + ":" + LedgerSmokeConfig.HTTP_PORT)
                .acceptHeader("application/json");

        // This is deliberately a one-user correctness smoke, not a capacity workload. It checks
        // routing, JSON shape, alternate-key lookup, and optional entry expansion before load tests.
        ScenarioBuilder smoke = scenario("Ledger HTTP query smoke")
                .exec(session -> session
                        .set("ledgerTxnId", fixture.ledgerTxnId())
                        .set("referenceId", fixture.referenceId()))
                .exec(
                        http("get transaction by ledger ID")
                                .get("/api/v1/ledger/transactions/#{ledgerTxnId}")
                                .queryParam("includeEntries", "true")
                                .check(
                                        status().is(200),
                                        jsonPath("$.transaction.ledgerTxnId").is("#{ledgerTxnId}"),
                                        jsonPath("$.transaction.referenceId").is("#{referenceId}"),
                                        jsonPath("$.entries.length()").is("2")
                                )
                )
                .exitHereIfFailed()
                .exec(
                        http("get transaction by reference ID")
                                .get("/api/v1/ledger/transactions")
                                .queryParam("referenceId", "#{referenceId}")
                                .queryParam("includeEntries", "true")
                                .check(
                                        status().is(200),
                                        jsonPath("$.transaction.ledgerTxnId").is("#{ledgerTxnId}"),
                                        jsonPath("$.transaction.referenceId").is("#{referenceId}"),
                                        jsonPath("$.entries.length()").is("2")
                                )
                );

        setUp(smoke.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
