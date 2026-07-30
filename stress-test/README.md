# Ledger Stress Test

This non-Spring Maven module contains Gatling simulations for ledger gRPC and
HTTP APIs. The gRPC simulation is a one-user smoke test for:

1. `PostService/postTransaction`
2. `PostService/getTxnByRefId`
3. `PostService/getTxnById`

Before measurement starts, the simulation calls `AccountService/createAccount`
to idempotently prepare the configured test account. The transaction reference
is unique per run, and the transaction ID returned by `postTransaction` is used
for `getTxnById`.

## Run With Maven

Start ledger-service first, then set the target when it is not the default
`172.31.16.37`. Running the plugin without a simulation selector executes both
the gRPC and HTTP smoke simulations:

```bash
mvn -pl stress-test -am -DskipTests install

LEDGER_GRPC_HOST=172.31.16.37 \
LEDGER_GRPC_PORT=9191 \
mvn -pl stress-test gatling:test
```

The HTML reports are written under `stress-test/target/gatling`. Gatling
continues to the second simulation when the first has an assertion failure, but
the Maven invocation still fails after all selected simulations have run.

Run only the HTTP query smoke test with:

```bash
mvn -pl stress-test \
  -Dgatling.simulationClass=com.exchange.stress.ledger.LedgerHttpQuerySmokeSimulation \
  gatling:test
```

It creates a transaction through internal gRPC before measurement, then verifies
both HTTP transaction query resources. Set `LEDGER_HTTP_HOST` and
`LEDGER_HTTP_PORT` when the HTTP endpoint is not `172.31.16.37:8081`.

## Run With Docker On The Load-Generator EC2

From the repository root:

```bash
mkdir -p stress-test/target/gatling
docker compose -f deploy/docker-compose.stress-test.yml up --build \
  --abort-on-container-exit --exit-code-from stress-test
```

By default, the container runs both smoke simulations. Select only one with
`GATLING_SIMULATION_CLASS`.

No inbound port is required on the load-generator EC2. Its security group needs
outbound access to ledger EC2 `172.31.16.37:9191` for fixture creation and
`172.31.16.37:8081` for the HTTP smoke.

Select the HTTP simulation in Docker with:

```bash
GATLING_SIMULATION_CLASS=com.exchange.stress.ledger.LedgerHttpQuerySmokeSimulation \
docker compose -f deploy/docker-compose.stress-test.yml up --build \
  --abort-on-container-exit --exit-code-from stress-test
```

The official Gatling Community gRPC component is limited to five users and five
minutes. This smoke test uses one user; larger capacity tests require Gatling
Enterprise or a different gRPC load driver.
