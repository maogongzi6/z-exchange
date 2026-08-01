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

## Statement Metadata Handoff

- `Executor.query/update` provides `MappedStatement` as a public method argument but is too broad for SQL timing.
- Executor interception converts that argument to `MapperName.method` and places it in a one-shot `ThreadLocal<StatementKey>`.
- The matching `StatementHandler` consumes and removes the key before starting its timer. Nested MyBatis queries therefore install and consume their own keys.
- The Executor `finally` block also removes the context for cache hits or failures before statement execution.
- This avoids reflection over private MyBatis `Plugin`, `RoutingStatementHandler`, or `BaseStatementHandler` fields.

The key must be captured before `StatementHandler` execution because `MappedStatement` is not exposed through the public `StatementHandler` API. The previous approach attempted to parse it later through paths such as `delegate.mappedStatement`. That required knowledge of private field names and proxy layout, so an interceptor-order or MyBatis-version change could silently produce `statement=unknown`. Capturing the public Executor argument first keeps mapper identification stable while leaving the latency measurement at the narrower StatementHandler boundary.

## Metadata And Failure Handling

- Missing or malformed `MappedStatement` metadata becomes `statement=unknown, command=unknown`.
- A `StatementHandler` call without matching Executor context becomes `statement=statement_context_missing, command=unknown`. Its timer count identifies an unexpected instrumentation path separately from malformed metadata.
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
