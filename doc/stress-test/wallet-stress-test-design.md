# Wallet-Service Stress-Test Design

## 1. Scope

Test these gRPC APIs with k6:

- Writes: `atomicTransaction`, `reserveTransaction`, and
  `applyReserveTransaction`.
- Queries: `getSnapshotByWalletId` and `getSnapshotByRefId`.
- Profiles: capacity discovery and stable-load soak.

Run one API operation per test. This keeps each capacity and latency result
independent. Test `applyReserveTransaction` release and earmark/consume modes
separately because only earmark/consume uses the ledger path.

## 2. Write Workloads

Use an open arrival-rate executor and rotate requests deterministically over a
configurable wallet pool. A large pool measures distributed capacity; a small
pool measures hot-wallet contention.

| Operation | Fixture and expected result |
| --- | --- |
| `atomicTransaction` | Funded source wallets and matching destination wallets/account mappings; gRPC accepts the transaction as pending, then ledger processing completes it. |
| `reserveTransaction` | Funded wallets; the call completes synchronously and creates reservations. |
| Apply release | Pre-created active reservations; the call completes synchronously without posting to ledger. |
| Apply earmark/consume | Pre-created active reservations and ledger mappings; gRPC accepts the transaction, then ledger processing settles it. |

Every logical request uses a unique reference and idempotency key. A
configurable percentage of attempts are exact duplicates that reuse the same
reference, idempotency key, and business payload. Include concurrent and
delayed duplicates. Count expected `REQUEST_IN_PROCESSING` results separately
from unexpected errors.

Important configuration:

| Variable | Purpose |
| --- | --- |
| `WALLET_WRITE_OPERATION` | `atomic`, `reserve`, `apply-release`, or `apply-earmark` |
| `WALLET_STRESS_WALLET_COUNT` | Wallet cardinality used by the workload |
| `WALLET_STRESS_DUPLICATE_PERCENT` | Exact duplicate attempt percentage; default `5` |
| `WALLET_STRESS_BASE_AMOUNT` | Minimum transaction amount in minor units |
| `WALLET_STRESS_AMOUNT_SPAN` | Deterministic amount range |

## 3. Fixture Headroom

Provision fixtures outside measured latency and use unique run-specific IDs to
avoid stale cache and asynchronous reply interference.

For reserve workloads, calculate each wallet's initial available balance from
the maximum configured request count, maximum amount, wallet count, and a
safety margin:

```text
per-wallet balance >=
  ceil(max unique requests / wallet count) * max amount * safety factor
```

Use a conservative upper bound that treats every offered attempt as unique.
This prevents insufficient funds even when the duplicate percentage or actual
distribution differs slightly. Pre-create enough independent reservations for
apply tests so reservation setup is not included in apply latency.

## 4. Asynchronous Settlement

gRPC success is acceptance, not completion, for atomic and apply-earmark
transactions. After load stops:

1. Stop new iterations and allow active RPCs to finish.
2. Poll until the run has no pending wallet transactions or pending/retry
   outboxes and all expected ledger replies have been applied.
3. Require the condition to remain stable for a short quiet period.
4. Fail when the configurable settlement timeout expires.
5. Run reconciliation only after settlement succeeds.

Use `WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS`,
`WALLET_STRESS_SETTLEMENT_POLL_SECONDS`, and
`WALLET_STRESS_SETTLEMENT_QUIET_SECONDS` for this bounded wait. Do not use an
unconditional fixed sleep.

## 5. Query Workloads

Run wallet-ID and reference-ID queries separately. Use immutable query
fixtures for capacity tests; mixed read/write traffic is a separate workload.

Important configuration:

| Variable | Purpose |
| --- | --- |
| `WALLET_QUERY_LOOKUP` | `wallet-id` or `ref-id` |
| `WALLET_QUERY_HIT_RATE_PERCENT` | Expected hit percentage |
| `WALLET_QUERY_WORKING_SET_SIZE` | Number of fixture wallets selected during a stage |
| `WALLET_QUERY_MISS_PATTERN` | `repeated` or `cold` |
| `WALLET_QUERY_MISS_CARDINALITY_PERCENT` | Missing-key set size relative to the hit working set |
| `WALLET_QUERY_OWNER_TYPE` | `user`, `system`, or `mixed` |
| `WALLET_QUERY_SYSTEM_PERCENT` | SYSTEM share when owner type is `mixed` |

Expected misses are successful test outcomes only when the API returns the
documented not-found error. A cold-miss run fails if its missing-key pool wraps.

## 6. Profiles

### Discovery

- Warm up at a low arrival rate.
- Increase RPC rate in fixed stages while holding all other dimensions stable.
- For queries, run a separate working-set discovery with fixed RPC rate.
- Drain and reconcile after the final stage.
- Repeat discovery at least three times around the capacity knee.

### Soak

- Warm up, then hold approximately 70-80% of sustainable capacity.
- Keep operation, wallet count, query dimensions, and duplicate percentage
  unchanged during the hold.
- Use a short run only for validation; use at least 30-60 minutes for the
  initial meaningful soak and a multi-hour follow-up when needed.
- Drain, wait for settlement, and reconcile.

## 7. Metrics

Record per operation:

- offered, completed, accepted/successful, expected-processing, and unexpected
  failure TPS;
- overall and successful p95/p99 latency, timeouts, and dropped iterations;
- active gRPC calls, DB/Hikari latency and saturation, Redis idempotency/cache
  results, and optimistic-lock failures;
- for ledger-backed writes: settlement latency, pending transaction count,
  outbox backlog/oldest age/retries, Kafka lag, and ledger reply rate.

Keep load-generator CPU, memory, sockets, and network below saturation.

## 8. Reconciliation and Acceptance

After drain and settlement, verify:

- balances are non-negative and total fixture funds are conserved;
- every reservation satisfies
  `total = remaining + consumed + pendingSettle + released`;
- accepted async transactions are finalized and no unexpected outbox backlog
  remains;
- exact duplicates created no additional transactions, actions, reservations,
  outboxes, ledger transactions, or ledger entries;
- persisted actions and amounts match deterministic requests;
- ledger-backed writes produced balanced ledger transactions;
- wallet-ID and reference-ID queries return the same final snapshot as the DB.

A run passes only when k6 thresholds, load-generator health, settlement, and
all reconciliation checks pass.
