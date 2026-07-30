# Ledger gRPC Stress Test

This non-Spring Maven module contains Gatling simulations for ledger gRPC APIs.
The first simulation is a one-user smoke test for:

1. `PostService/postTransaction`
2. `PostService/getTxnByRefId`
3. `PostService/getTxnById`

Before measurement starts, the simulation calls `AccountService/createAccount`
to idempotently prepare the configured test account. The transaction reference
is unique per run, and the transaction ID returned by `postTransaction` is used
for `getTxnById`.

## Run With Maven

Start ledger-service first, then set the target when it is not the default
`172.31.16.37:9191`:

```bash
mvn -pl stress-test -am -DskipTests install

LEDGER_GRPC_HOST=172.31.16.37 \
LEDGER_GRPC_PORT=9191 \
mvn -pl stress-test gatling:test
```

The HTML report is written under `stress-test/target/gatling`.

## Run With Docker On The Load-Generator EC2

From the repository root:

```bash
mkdir -p stress-test/target/gatling
docker compose -f deploy/docker-compose.stress-test.yml up --build \
  --abort-on-container-exit --exit-code-from stress-test
```

No inbound port is required on the load-generator EC2. Its security group needs
outbound access to ledger EC2 `172.31.16.37:9191`.

The official Gatling Community gRPC component is limited to five users and five
minutes. This smoke test uses one user; larger capacity tests require Gatling
Enterprise or a different gRPC load driver.
