# Ledger Query Stress-Test Flow

## Goal

Measure `getTxnById` and `getTxnByRefId` independently with
`includeEntries=true`. The test separates RPC capacity, working-set effects,
and missing-key behavior so one variable does not hide another.

## Dataset

Before k6 starts, SQL creates a configurable set of immutable transactions:

- deterministic transaction and reference IDs
- two balanced entries per transaction
- dedicated `k6-query-*` identifiers

Fixture provisioning is outside measured latency. The configured working set
must not exceed the fixture size.

## Test Dimensions

Run transaction-ID and reference-ID queries separately. They use different
cache paths and must not be aggregated into one capacity result. In the current
ledger configuration, reference misses can use negative caching while
transaction-ID negative caching is disabled.

### RPC Discovery

- Keep `LEDGER_QUERY_WORKING_SET_SIZE` fixed.
- Warm at a low rate.
- Increase RPC in steps and hold each step.
- Stop after the highest step; there is no reduced-load recovery phase.

Use this profile to find the throughput/latency knee for one fixed data set.

### Working-Set Discovery

- Keep `LEDGER_QUERY_RATE` fixed.
- Start with `LEDGER_QUERY_WORKING_SET_START` IDs.
- Increase the set by `LEDGER_QUERY_WORKING_SET_STEP` each stage.
- Hold each set size for `LEDGER_QUERY_STAGE_SECONDS`.

Use this profile to observe cache and database behavior as locality decreases.

### Soak

- Warm briefly.
- Hold one configured RPC and working set for the full soak stage.
- Do not change either variable during the hold.

## Missing Queries

`LEDGER_QUERY_MISS_RATE_PERCENT` controls the percentage of requests that use
absent IDs. `LEDGER_QUERY_MISS_CARDINALITY_PERCENT` defines the missing-key set
size relative to the current hit working set. Increasing miss rate therefore
does not increase missing-key cardinality.

- `repeated`: cycle through the bounded missing-key set to exercise repeated
  misses and negative-cache behavior.
- `cold`: use every configured missing key once. If the pool wraps, k6 records
  `ledger_query_cold_miss_reuse` and fails the run because it is no longer cold.

Use a unique run ID for each miss run so previous negative-cache entries do not
turn a cold test into a warm one.

## Expected Results

Each request is classified as exactly one of:

| Result | Expectation |
| --- | --- |
| Hit | gRPC OK, matching transaction identifier, exactly two entries |
| Expected miss | gRPC OK with `ERROR_NOT_FOUND` |
| Unexpected business result | Any other reply payload |
| Transport failure | Non-OK gRPC status or invocation exception |

Expected not-found replies are not errors. A cold-key reuse, malformed hit,
unexpected business result, or transport failure invalidates the affected
request.

The current processor logs not-found results at error level. Miss-heavy tests
therefore include that logging cost and can generate substantial log volume;
record it when comparing hit and miss capacity.

## Primary Evidence

- offered, completed, successful-hit, and expected-miss TPS
- overall, hit, and expected-miss p95/p99 latency
- unexpected error rate and dropped iterations
- ledger gRPC/business latency and active calls
- cache result, Redis client latency, DB operation/transaction latency, and
  Hikari connection metrics

Run only one query method and one discovery dimension at a time. Record the
application version, configuration, run ID, dashboard range, and k6 summary.
