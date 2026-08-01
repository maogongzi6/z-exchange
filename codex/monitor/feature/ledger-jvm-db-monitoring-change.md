# Ledger JVM and DB Monitoring Change

## Scope

This change adds first-phase JVM and database correlation signals for ledger
write stress tests. It is limited to:

- client-observed DB transaction duration;
- existing gRPC, business, JVM GC, and Hikari metrics;
- provisioned Grafana panels for correlation.

Redis dashboards/exporters, MySQL exporters, repository-operation timing, and
new business-flow behavior are excluded.

## Code Changes

### DB Transaction Timer

```text
zexchange.db.transaction.duration
type: Timer
tag: outcome = success | error
```

Transaction execution and measurement use separate components:

- `DbTxnExecutor` defines the application-facing contract.
- `DefaultDbTxnExecutor` owns transaction execution and rollback behavior.
- `MeteredDbTxnExecutor` decorates the default executor and records duration.

The registered application bean is the metered decorator. The undecorated
executor remains internal to configuration so business code cannot
accidentally bypass observability. The metric covers the full outer transaction
call, including connection acquisition, transaction begin, callback execution,
commit, and rollback.

- A successful committed `Result` records `success`.
- A failed `Result`, callback exception, or commit failure records `error`.
- Success and error timers are registered once and reused on the hot path.
- No SQL, request, transaction ID, or exception labels are introduced.

The timer currently aggregates all `DbTxnExecutor` calls for the selected
ledger instance. No transaction-name tag is added in this narrow phase.

## Dashboard Changes

The `Ledger Service Overview` dashboard adds a `JVM and Database` row:

| Panel | Signals | Meaning |
|---|---|---|
| Ledger Write Latency and GC Correlation | gRPC overall/OK p95, business overall/success p95, DB overall/success p95, GC pause max | Identifies whether write spikes align with successful work, DB work, or JVM pauses. |
| Hikari Connections | active, max, pending | Shows pool utilization and waiting callers. |
| Hikari Acquisition Latency | rolling average and max | Shows time spent obtaining a pooled connection. |
| Hikari Connection Timeouts | timeout rate | Shows requests that could not obtain a connection in time. |

All latency series are displayed in milliseconds and filtered by the selected
ledger instance.

The API and business sections also compare overall p95 with filtered latency:

- gRPC `OK` means transport success and may still contain a protobuf business error.
- Business `success` represents a successful processor result.
- DB `success` represents a transaction that returned a successful `Result` and committed.

Overall histograms remain visible so failures and timeouts are not excluded.

## Configuration And Deployment

- No dependency or Prometheus scrape change is required.
- JVM and Hikari metrics already come from the installed Actuator.
- Ledger already configures histogram buckets for
  `zexchange.db.transaction.duration`.
- Ledger-service must be rebuilt/redeployed to emit the new DB timer.
- Grafana polls the provisioned dashboard directory every 30 seconds; the
  bind-mounted JSON does not require an image rebuild.

## Verification

Unit coverage separately verifies core transaction behavior and metric outcome
classification. Dashboard JSON is parsed during local verification.
