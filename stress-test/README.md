# Ledger Stress Test

This directory contains a k6 gRPC smoke test and is not a Maven or Spring
module. The smoke verifies one dependent workflow:

1. `PostService/postTransaction`
2. `PostService/getTxnByRefId`
3. `PostService/getTxnById`

The transaction reference is unique per run. Both query calls validate the
transaction created by the write call and require the two ledger entries.

## Fixture Provisioning

`run-smoke.sh` connects directly to the ledger MySQL database before starting
k6 and applies `fixtures/ledger-smoke-fixture.sql`. The SQL idempotently creates
these dedicated rows:

| Fixture | Value |
| --- | --- |
| Asset ID | `k6-smoke-asset` |
| Account ID | `k6-smoke-account-id` |
| Account reference | `k6-smoke-account` |

Fixture setup is completed before k6 starts, so it is excluded from gRPC
latency. Run this only against a test database. The database credentials must
permit `INSERT` and `UPDATE` on `assets` and `accounts`.

## Run With Docker

From the repository root:

```bash
docker compose -f deploy/docker-compose.stress-test.yml up --build \
  --abort-on-container-exit --exit-code-from stress-test
```

For the AWS VPC deployment, provide the same database settings used by
ledger-service:

```bash
INFRA_PRIVATE_IP=172.31.18.211 \
LEDGER_GRPC_HOST=172.31.16.37 \
LEDGER_DB_USERNAME=ledger \
LEDGER_DB_PASSWORD='replace-with-the-infrastructure-value' \
docker compose -f deploy/docker-compose.stress-test.yml up --build \
  --abort-on-container-exit --exit-code-from stress-test
```

`LEDGER_GRPC_HOST` overrides the gRPC target directly. If it is not set, the
Compose default is `172.31.16.37`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `LEDGER_GRPC_HOST` | `172.31.16.37` | Ledger gRPC host |
| `LEDGER_GRPC_PORT` | `9191` | Ledger gRPC port |
| `LEDGER_GRPC_DEADLINE_SECONDS` | `5` | Connection timeout, per-RPC timeout, and latency threshold |
| `LEDGER_TEST_AMOUNT` | `100` | Debit and credit amount; passed as protobuf `int64` |
| `INFRA_PRIVATE_IP` | `172.31.18.211` | Fixture database host |
| `DB_PORT` | `3306` | Fixture database port |
| `LEDGER_DB_NAME` | `trade_ledgerservice` | Ledger schema |
| `LEDGER_DB_USERNAME` | `ledger` | Fixture database user |
| `LEDGER_DB_PASSWORD` | `ledger-local` | Fixture database password |
| `DB_READY_ATTEMPTS` | `30` | Two-second database readiness attempts |
| `DB_CONNECT_TIMEOUT_SECONDS` | `5` | Maximum duration of each database connection attempt |

The load-generator EC2 requires outbound VPC access to:

- ledger EC2 port `9191`
- infrastructure EC2 port `3306`

No inbound port is required on the load-generator EC2.

## Files

- `scripts/ledger-grpc-smoke.js`: commented k6 workflow and assertions.
- `fixtures/ledger-smoke-fixture.sql`: idempotent Asset and Account fixture.
- `run-smoke.sh`: waits for MySQL, applies the fixture, then starts k6.
- `Dockerfile`: combines the pinned k6 binary with MySQL 8.4's native client.
