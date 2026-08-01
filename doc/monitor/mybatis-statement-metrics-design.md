# MyBatis Statement Metrics Design

## Purpose

Measure client-observed SQL execution latency and normalized DB errors close to JDBC execution, without changing business or transaction behavior.

## Metrics

| Metric | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `zexchange.db.operation.duration` | Timer | `statement`, `command`, `outcome` | Time spent in intercepted MyBatis `query`, `update`, or `batch` execution. |
| `zexchange.db.operation.errors` | Counter | `statement`, `command`, `error_type` | Failed statement executions classified through `DbExceptionTranslator`. |

`statement` is shortened to `MapperName.method`. `command` is bounded to `select`, `insert`, `update`, `delete`, or `unknown`. Timer `outcome` is `success` or `error`.

## Timing Boundary

The interceptor surrounds `StatementHandler.query/update/batch`. It measures JDBC execution and returned-result handling performed inside that call. It does not include connection acquisition, statement preparation, parameter binding, or transaction commit/rollback.

## Metadata And Failure Handling

- Missing or malformed MyBatis metadata becomes `statement=unknown, command=unknown`.
- Null keys passed to metric recording are also normalized to the unknown key.
- Metric extraction, registration, classification, and recording failures are suppressed so they cannot alter SQL results.
- Failures are not logged on this hot path. Rate-limited logging can be added later if operational diagnosis requires it.

## Timer Cache And Cardinality

- Successfully registered timers are cached by `StatementKey` in separate success and error caches; normal cache hits are lock-free.
- The default admission limit is 512 named timer series across both outcomes.
- Admission uses `AtomicInteger` and `ConcurrentHashMap` without an explicit lock. Concurrent first-time registrations can exceed the limit slightly; this bounded approximation favors throughput over an exact cap.
- After the limit is observed, unseen statement IDs share a cached `cardinality_overflow` timer for their outcome instead of registering more named timers.
- The overflow timer uses `command=unknown`; it signals cardinality overflow rather than identifying a specific mapper operation.

Error counters have no additional application-side cache because errors should be rare. Micrometer's registry still manages meter identity, and error counters retain the normalized original statement tags for diagnosis.

## Dashboard Usage

- The normal DB operation latency panel filters `outcome="success"` and shows p95/p99 per statement.
- The error panel shows the error-counter rate by statement, command, and error type.
- Error latency remains recorded with `outcome="error"` for temporary diagnostic panels.
