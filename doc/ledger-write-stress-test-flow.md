# Ledger Write Stress-Test Flow

## 1. Purpose

This test measures the sustainable write capacity of
`PostService/postTransaction`, verifies recovery after a controlled overload,
and checks that ledger and idempotency invariants still hold under concurrency.

This phase tests only **distributed accounts**. Hot-account behavior is a
separate workload because it measures contention rather than general ledger
capacity.

The test consists of three independent runs:

1. Capacity discovery
2. Overload and recovery
3. Stable-load soak

Run the existing gRPC smoke test before these runs. Do not include fixture
creation or SQL verification in measured request latency.

## 2. Test Environment

Use a dedicated test environment with the same ledger-service, JVM, database,
Redis, Kafka, and EC2 sizing intended for the measured deployment. Record the
application version, infrastructure configuration, k6 configuration, and test
`run_id` with every result.

The k6 host must have enough CPU, memory, network capacity, sockets, and
preallocated VUs to generate the target arrival rate. A saturated load
generator invalidates the server-capacity result.

Before every run:

- Start from a known database fixture.
- Allow JVM and infrastructure metrics to return to their baseline.
- Confirm that no previous test has an active request or outbox backlog.
- Use a new `run_id` so database rows can be isolated from other runs.

## 3. Fixture Preparation

Create the fixture with idempotent SQL before starting k6:

- 10 assets
- 100 accounts, distributed evenly across the assets
- 10 accounts for each asset
- Stable, test-only asset IDs and account references

An account must be used only with its configured asset. Account selection must
be uniform and rotated across the complete fixture. A 20-entry transaction may
reuse accounts because an asset has 10 fixture accounts, but the generator
must rotate the starting account so repeated requests do not favor the same
accounts.

Use a reference format such as:

```text
k6-write:{run_id}:{logical_transaction_sequence}
```

Keep the reference within the database's 64-character limit.

## 4. Transaction Data Pattern

Choose the entry count independently for every new logical transaction:

| Entry count | Percentage | Purpose |
| ---: | ---: | --- |
| 2 | 45% | Normal small transaction |
| 4 | 45% | Normal multi-entry transaction |
| 20 | 10% | Large transaction |

Use one asset per transaction. Construct entries as balanced debit/credit
pairs:

- 2 entries: 1 debit and 1 credit
- 4 entries: 2 debits and 2 credits
- 20 entries: 10 debits and 10 credits

For every transaction and asset:

```text
sum(debit amounts) == sum(credit amounts)
```

Use positive integer minor-unit amounts. Generate the workload deterministically
from the `run_id` and logical sequence, or prepare an input manifest before the
measured run. The manifest should contain the reference, asset, entry count,
accounts, directions, and amounts. Do not log every generated request during
the test because high-volume logging distorts performance.

## 5. Duplicate Request Pattern

Five percent of **request attempts** should be duplicates. A duplicate must
reuse the original request's reference ID and byte-equivalent business payload.
Changing the payload would test hash conflicts instead of idempotent replay.

Cover these duplicate timings without changing the total 5% duplicate ratio:

- While the original request is still in flight
- After the original has completed
- Multiple duplicate attempts for a subset of logical transactions

An in-flight duplicate may return `REQUEST_IN_PROCESSING`. This is an expected
idempotency-contention outcome and is **not** counted as a business error when
the attempt is known by k6 to be an in-flight duplicate.

It must still be counted separately:

```text
request_in_processing TPS and rate
```

This distinction must not hide defects:

- `REQUEST_IN_PROCESSING` on a non-duplicate request is unexpected.
- A delayed duplicate should eventually replay the original result rather than
  remain permanently in processing.
- Each duplicate group must resolve to exactly one persisted ledger
  transaction with exactly one set of entries.
- If the original times out, its outcome is ambiguous until reconciled by
  reference ID. A client timeout must not be classified immediately as a
  failed transaction.

Measure original-request latency separately from duplicate-request latency.
Fast duplicate responses must not artificially improve the primary write p95.

## 6. Result Classification

Track these client-side outcomes separately:

| Outcome | Classification |
| --- | --- |
| New transaction created | Successful write |
| Identical request replayed with the original result | Successful idempotent response |
| Known in-flight duplicate returns `REQUEST_IN_PROCESSING` | Expected processing outcome |
| Same reference with different payload returns hash conflict | Correctness failure in this workload |
| gRPC transport failure or deadline exceeded | Transport error or ambiguous outcome |
| Any other business error | Unexpected business error |

The main error-rate threshold is:

```text
unexpected errors / all completed attempts
```

Do not include expected `REQUEST_IN_PROCESSING` responses in that rate. Show
their rate on the dashboard independently. A high or continuously growing
processing rate is still operationally important even though it is expected
for deliberately concurrent duplicates.

## 7. Live Metrics

### Required

- k6 offered TPS
- Completed and successful TPS
- Original write-request p95 latency
- Unexpected error rate

### Useful Confirmation

- Active gRPC requests: `zexchange.grpc.server.active`

The client should expose separate counters for offered attempts, completed
RPCs, successful writes/idempotent replies, expected processing responses, and
unexpected failures. The configured arrival rate is not proof that k6 actually
generated or completed that rate.

Use p95 during live observation. Retain p50, p99, maximum latency, dropped
iterations, and deadline counts for post-test analysis.

Thirty-second stages are intentionally short. They are suitable for an initial
small-server evaluation only when each stage contains enough samples. Repeat
each experiment at least three times and do not accept a saturation point that
appears in only one run.

## 8. Run 1: Capacity Discovery

Use an open `ramping-arrival-rate` scenario. Control requests per second rather
than concurrent users.

1. Warm up for 30 seconds at a low rate, initially 5 TPS.
2. Increase the target arrival rate by a configured step every 30 seconds.
3. Keep the transaction-size and duplicate distributions unchanged at every
   rate.
4. Continue through the latency knee into a brief, controlled overload.
5. Stop before prolonged timeouts or backlog growth can destabilize the test
   environment.
6. Set the arrival rate to zero and allow in-flight requests and durable work
   to drain before reconciliation.

Choose the rate step based on the expected server size. If the first run has no
prior estimate, use coarse steps to locate the region and a second discovery
run with smaller steps around that region.

The saturation knee is the earliest repeatable level where one or more of
these behaviors appears:

- Offered TPS increases but completed/successful TPS increases very little or
  stops increasing.
- Original-request p95 rises disproportionately between consecutive levels.
- Unexpected error or deadline rate begins increasing.
- Active gRPC requests grow instead of returning to a stable level.

A useful initial heuristic is:

```text
offered TPS increases by 10-20%
while completed TPS increases by less than 5%,
or p95 increases by more than 50% between adjacent levels
```

Treat this as a comparison rule, not a universal SLO. The sustainable capacity
is the last stable level before the knee, not the highest TPS observed during
overload.

## 9. Run 2: Overload And Recovery

Use the sustainable capacity found in Run 1. All phases must occur in the same
k6 run without restarting ledger-service or its dependencies.

| Phase | Duration | Target rate |
| --- | ---: | ---: |
| Warm-up | 30 seconds | Low rate |
| Stable baseline | 30 seconds | 80% of sustainable capacity |
| Ramp to overload | 30 seconds | 110-120% of sustainable capacity |
| Hold overload | 30 seconds | 110-120% of sustainable capacity |
| Reduce load | 30 seconds | 50-70% of sustainable capacity |
| Recovery observation | 60 seconds | 50-70% of sustainable capacity |
| Drain | 30 seconds | Ramp to 0 TPS |
| Idle observation | 30 seconds | 0 TPS |

The test passes recovery only when:

- Completed TPS again follows offered TPS at the reduced rate.
- Original-request p95 returns near its pre-overload baseline.
- Unexpected errors return to the baseline rate.
- Active gRPC requests return to baseline rather than remaining queued.
- All ambiguous requests are reconciled.
- Outbox and other durable backlog return to their pre-test level.

Reducing only slightly below the saturation knee may not allow recovery. Use
50-70% initially; later tests can find the minimum reduction required.

## 10. Run 3: Stable-Load Soak

Use a separate environment state and `run_id`:

| Phase | Duration | Target rate |
| --- | ---: | ---: |
| Warm-up | 30 seconds | Low rate |
| Ramp | 30 seconds | 80% of sustainable capacity |
| Soak | 2 minutes | 80% of sustainable capacity |
| Drain | 30 seconds | Ramp to 0 TPS |
| Idle observation | 30 seconds | 0 TPS |

During the soak, latency, successful TPS, error rate, and active requests must
remain stable. Two minutes is a short initial soak and will not expose slow
memory leaks, connection leaks, GC trends, or periodic background-job effects.
Use a longer follow-up soak after the workload and thresholds are validated.

## 11. Drain And Reconciliation

k6 stopping new iterations does not prove that all durable work completed.
After every run, wait until:

- No k6 iteration remains in flight.
- Active gRPC requests return to baseline.
- Outbox pending work returns to baseline or reaches its expected final state.
- Timed-out requests have been resolved by reference ID.

Then compare the database with the deterministic input manifest.

### Transaction And Entry Counts

For every unique logical reference that committed:

- Exactly one `ledger_transactions` row exists.
- The number of `ledger_entries` rows equals the generated entry count.
- Duplicate attempts did not add entries or another transaction.

### Double-Entry Per Transaction And Asset

`direction=1` is credit and `direction=2` is debit in the current schema.
The following query must return zero rows after replacing the run prefix:

```sql
SELECT
    le.txn_id,
    le.asset_id,
    SUM(CASE WHEN le.direction = 1 THEN le.amount ELSE 0 END) AS credit_sum,
    SUM(CASE WHEN le.direction = 2 THEN le.amount ELSE 0 END) AS debit_sum
FROM ledger_entries le
JOIN ledger_transactions lt ON lt.txn_id = le.txn_id
WHERE lt.reference_id LIKE 'k6-write:{run_id}:%'
GROUP BY le.txn_id, le.asset_id
HAVING credit_sum <> debit_sum;
```

### Account And Asset Totals

Aggregate persisted entries by `(account_id, asset_id, direction)` and compare
them with the manifest's expected totals for unique committed transactions.
Debit and credit do not need to be equal for each individual account. Compare
the signed net movement instead:

```text
account net = total credits - total debits
```

Do not calculate expectations from raw requests sent because duplicates must
not create additional effects. Do not assume a timed-out request failed; use
its persisted final state.

## 12. Acceptance Criteria

A run is valid only when:

```text
k6 generated the configured offered rate
AND the load generator remained healthy
AND measured latency and unexpected errors met the run's criteria
AND expected duplicate contention was classified separately
AND every ambiguous request was reconciled
AND every transaction remained balanced per asset
AND duplicates produced no additional durable effects
AND active and durable backlog returned to baseline
```

Store the k6 summary, dashboard time range, application version, run
configuration, SQL reconciliation output, and any relevant sampled error logs
as one test result.
