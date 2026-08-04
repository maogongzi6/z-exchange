# Wallet Service Ledger-Parity Change Scope

## Scope

This evaluation compares the current `wallet-service` with patterns already used by
`ledger-service` and `common-util`. Deployment files, environment endpoints, and server
provisioning are excluded.

## Assessment

| Priority | Area | Current state | Change scope |
| --- | --- | --- | --- |
| P0 | Ledger reply correctness | Wallet parses `PostTransactionReplyPb` but ignores `error` and always calls completion logic. | Branch on reply outcome. Only a successful ledger reply may call `AfterPostLedgerProcessor`. |
| P0 | Kafka listener | Wallet uses the legacy blocking `DefaultErrorHandler` path and handles only envelope parse errors. | Adopt the common listener template, explicit failure classification, `@RetryableTopic`, and DLT handling. |
| P0 | Failed-reply recovery | A failed ledger reply has no durable wallet-side record or reconciliation trigger. | Keep the transaction pending, persist enough failure/reconciliation state before ack, and never rely on logs alone. The storage shape needs a separate decision. |
| P1 | API metrics | Actuator is enabled, but no global gRPC interceptor or wallet business metrics exist. | Register the common gRPC interceptor and add an explicit wallet business metric wrapper. |
| P1 | Cache/idempotency metrics | Redis support metrics exist, but wallet cache strategies are unmetered and business code bypasses `IdempotencyClient`. | Wrap all four cache strategies and migrate wallet idempotency calls to the measured wrapper. |
| P1 | DB exception parity | Wallet imports common DB components but lacks AOP enablement/dependency used by ledger. | Enable repository/transaction exception translation and retain existing MyBatis/transaction timers. |
| P2 | Runtime safeguards | Wallet lacks ledger's bounded Hikari and MyBatis statement timeout settings. | Add equivalent service-appropriate timeouts after validating wallet load characteristics. This is runtime safety, not deployment work. |
| P2 | Legacy client | `PostServiceClient` and `grpc.client.ledger-client` are unused; posting is Kafka/outbox based. | Remove them after confirming no external reflective use. Keep `AccountServiceClient`. |

## 1. Kafka Change Scope

### Already Sound

- Keep `EventEnvelopePb` as the Kafka value and protobuf bytes as its payload.
- Keep wallet command creation in the same DB transaction as wallet state changes.
- Keep the existing deterministic outbox row across publish retries.
- Keep repository-only mapper access; wallet business code already follows this boundary.

### Required Changes

1. Replace wallet's listener-owned parsing/orchestration with a thin Spring Kafka adapter and a
   wallet-owned ledger-reply handler.
2. Use `AckOnlyDefaultMessageHandler` with an explicit failure policy. Do not use its default
   ack-on-failure policy because that could silently discard a ledger result.
3. Use the common listener's ack-failure marker so a retry after durable processing only retries
   acknowledgment.
4. Add wallet retry properties, retry-topic configuration, and `@DltHandler`. Retry-topic/DLT
   topics remain provisioned outside application code.
5. Do not reuse `ListenerRetryPolicy.replyFailureAfterAttempts` as though wallet creates a failure
   reply. For this ack-only listener, retry exhaustion must route the original ledger reply to DLT.
6. Add a durable policy for ledger failure replies. Under the current domain model they are
   non-terminal: the wallet transaction remains `PENDING`; only a later success may complete it.

### Required Scenario Behavior

| Scenario | Expected behavior |
| --- | --- |
| Outer envelope cannot be parsed | Direct DLT; request identity and payload contract are unknown. |
| Event type or reply payload is invalid | DLT; do not acknowledge and silently lose the ledger result. |
| Ledger success reply | Apply the guarded wallet completion transaction, then ack. |
| Duplicate success reply after completion | Idempotent no-op/success, then ack. |
| Ledger failure reply | Persist failure/reconciliation evidence, leave transaction pending, then ack. |
| Retryable DB failure while applying success | Throw `KafkaListenerRetriableException`; do not ack. |
| Non-retryable invariant/state conflict | DLT or durable reconciliation; never blindly complete. |
| Unexpected exception | DLT with bounded logging; do not use the default ack-only downgrade. |
| Ack failure after durable work | Retry with the ack-failed header and skip business processing next time. |

## 2. Metric Change Scope

### API and Business Metrics

Add `WalletMetricsConfig`, metric definitions/tags, and `WalletBusinessMetrics`. Instrument the
explicit service boundary rather than applying general AOP to processors.

| Metric | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `zexchange.grpc.server.active` | Gauge | `service`, `method` | Current in-flight wallet gRPC calls. Reuse the common interceptor. |
| `zexchange.grpc.server.duration` | Timer | `service`, `method`, `grpc_status` | Wallet gRPC latency and request count through timer `_count`. |
| `zexchange.wallet.requests` | Counter | `operation`, `ingress`, `outcome`, `error_code` | Wallet business request count and error rate. |
| `zexchange.wallet.processing.duration` | Timer | `operation`, `ingress`, `outcome` | Wallet processor latency, separate from transport framing. |
| `zexchange.wallet.transactions.completed` | Counter | `source`, `transaction_type` | Successful guarded transition to `COMPLETED`; duplicates must not increment it. |

Bounded values should cover the current gRPC operations and the Kafka completion path. Never tag
wallet ID, transaction ID, reservation ID, reference ID, idempotency key, or error detail.

### Shared Lower-Level Metrics

- Wrap `balanceRefCache`, `hotBalanceRefCache`, `normalBalanceCache`, and `hotBalanceCache` with
  `MetricRawCacheStrategy`/`MetricVersionCacheStrategy`, using four bounded cache-type values.
- Replace direct `IdempRedisClient` use in `IdempPrecheckProcessor` and
  `BeforePostLedgerProcessor` with `IdempotencyClient`, including wrapper-owned decode/hash checks.
- Keep existing common metrics for Redis operation duration, DB statement duration/errors, and DB
  transaction duration. Do not add duplicate wallet-specific timers for the same operations.
- Add histogram/SLO configuration for gRPC, wallet processing, Redis, and DB timers.

### Shared Gap, Not Wallet-Only Work

`ledger-service` config names Kafka listener/publisher and outbox timers, but current code does not
emit those metrics. Implement them in `common-util` first and adopt them in both services; do not
create a second wallet-only metric implementation. The same applies to outbox backlog and oldest
pending-age collectors.

## 3. Supporting Changes and Tests

- Add `spring-boot-starter-aop` and enable AspectJ proxying in wallet so common DB exception
  translation covers repository and transaction boundaries.
- Add unit tests for reply success, reply error, malformed payload, retryable DB failure,
  unexpected failure, duplicate completion, retry exhaustion, and ack-failure recovery.
- Add configuration tests for retry thresholds and the retry-topic container factory.
- Add metric tests using `SimpleMeterRegistry`; assert exact counts and that active gauges return to
  zero.
- Add an Embedded Kafka test later for retry-topic/DLT routing. Unit tests alone cannot prove Spring
  topic routing.

## 4. Explicitly Excluded or No Change

- No Docker, Compose, cloud profile, IP, DNS, security-group, or log deployment changes.
- No wallet balance/reservation algorithm refactor in this scope.
- No direct mapper migration is needed; production code already accesses mappers through repositories.
- No publisher/outbox dispatcher rewrite solely for parity; their current implementations match
  ledger's. Shared publisher/outbox metrics should be implemented once in common infrastructure.
- No high-cardinality labels or scrape-time DB queries.

## Recommended Order

1. Correct ledger reply semantics and add focused tests.
2. Migrate wallet to the common retry-topic listener template and prove DLT behavior.
3. Enable DB exception translation and migrate idempotency/cache wrappers.
4. Add gRPC/business metrics and histogram configuration.
5. Implement shared Kafka/outbox metrics for both services in a separate phase.
