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
| P1 | DB exception parity | Wallet imports the common DB beans and receives AOP classes transitively, but does not enable AspectJ proxies or declare AOP as a direct service dependency. | Enable repository/transaction exception translation and retain existing MyBatis/transaction timers. |
| P2 | Runtime safeguards | Wallet lacks ledger's bounded Hikari and MyBatis statement timeout settings. | Add equivalent service-appropriate timeouts after validating wallet load characteristics. This is runtime safety, not deployment work. |
| P2 | Legacy client | `PostServiceClient` and `grpc.client.ledger-client` are unused; posting is Kafka/outbox based. | Remove them after confirming no external reflective use. Keep `AccountServiceClient`. |

## Phase 1: Kafka Listener Template Migration (P0)

### Goal

Migrate the ledger-reply consumer from the blocking `DefaultErrorHandler` path to the common
listener template and `@RetryableTopic`. Wallet consumes a terminal reply and sends no reply of
its own, so it must use `AckOnlyDefaultMessageHandler`, not `OutboxReplyDefaultMessageHandler`.

### Already Sound

- Keep `EventEnvelopePb` as the Kafka value and protobuf bytes as its payload.
- Keep wallet command creation in the same DB transaction as wallet state changes.
- Keep the existing deterministic outbox row across publish retries.
- Keep repository-only mapper access; wallet business code already follows this boundary.

### Required Changes

| Change point | Scope |
| --- | --- |
| Spring adapter | Refactor wallet `DefaultListener` into a thin adapter over the common listener. Accept `ConsumerRecord<String, byte[]>`, pass value/headers/ack to the delegate, and add `@RetryableTopic` plus `@DltHandler`. |
| Wallet handler | Extract envelope event validation, `PostTransactionReplyPb` parsing, reply-outcome branching, and `AfterPostLedgerProcessor` invocation into a wallet-owned handler. Only `ERROR_OK` may invoke completion. |
| Ack-only action | Register the handler through `AckOnlyDefaultMessageHandler`. Supply an explicit failure policy; its default ack-on-failure constructor is unsafe for wallet. |
| Failure classification | Convert recoverable processing failures, especially retryable `DbException`, to `KafkaListenerRetriableException`. Route malformed payloads, unsupported event types, non-retryable failures, unexpected failures, and exhausted retries to DLT without acknowledging them as successful. |
| Retry ownership | Use `concurrentRetryTopicCommandKafkaListenerContainerFactory`; wallet must no longer depend on `KafkaErrorHandlingRegister`/`DefaultErrorHandler` for this listener. Let `@RetryableTopic.attempts` own final DLT routing; do not introduce an earlier failed-reply threshold because AckOnly creates no failure outbox. |
| Ack recovery | Reuse the common ack-failure header behavior. Once wallet DB work is durable, a redelivery caused only by ack failure must skip business processing and retry acknowledgment. |
| Configuration | Add wallet listener retry properties and `application.yml` values for attempts, backoff, and topic auto-creation. Retry/DLT topics remain infrastructure-provisioned in production. |
| DLT logging | Log topic, partition, offset, key, original topic, exception class, and exception message from Kafka headers. Do not declare an `Exception` argument on `@DltHandler`. |

### Wallet Files

- Refactor `kafka/consumer/DefaultListener.java` as the Spring adapter and DLT endpoint.
- Add a wallet ledger-reply handler under `kafka/consumer`.
- Add `kafka/config/WalletListenerConfig.java` and `WalletListenerRetryProperties.java`.
- Add retry settings to `src/main/resources/application.yml`.
- Add focused listener, handler, configuration, and DLT-header tests.

### Dependency On Reply Semantics

This phase must not preserve the current behavior that ignores `PostTransactionReplyPb.error`.
A failed ledger reply leaves the wallet transaction `PENDING` and is retained in DLT for interim
reconciliation. A wallet-side failure record remains a separate storage decision; once implemented,
the listener may persist that record and acknowledge the source reply instead.

### Required Scenario Behavior

| Scenario | Expected behavior |
| --- | --- |
| Outer envelope cannot be parsed | Direct DLT; request identity and payload contract are unknown. |
| Event type or reply payload is invalid | DLT; do not acknowledge and silently lose the ledger result. |
| Ledger success reply | Apply the guarded wallet completion transaction, then ack. |
| Duplicate success reply after completion | Idempotent no-op/success, then ack. |
| Ledger failure reply | Do not run completion. Leave the transaction pending and route the original reply to DLT until wallet-side failure persistence exists. |
| Retryable DB failure while applying success | Throw `KafkaListenerRetriableException`; do not ack. |
| Non-retryable invariant/state conflict | DLT or durable reconciliation; never blindly complete. |
| Unexpected exception | DLT with bounded logging; do not use the default ack-only downgrade. |
| Ack failure after durable work | Retry with the ack-failed header and skip business processing next time. |

## Phase 2: DB Exception Translation Parity (P1)

### Goal

Activate the common DB exception translator for wallet repository and transaction boundaries without
changing wallet business flow, transaction boundaries, or metric semantics.

### Already Sound

- `CommonDbComponentRegister` already supplies `DbExceptionTranslator`, `DbExceptionTranslateAop`,
  the metered `DbTxnExecutor`, and the MyBatis statement metrics interceptor.
- All wallet production mapper access is contained in repositories, and every wallet repository
  extends `DbBaseRepository`. The shared `OutboxRepository` follows the same boundary.
- `CreateWalletProcessor`, `BeforePostLedgerProcessor`, and `AfterPostLedgerProcessor` already use
  the injected `DbTxnExecutor`; no transaction call-site migration is needed.

### Required Changes

| Change point | Scope |
| --- | --- |
| AOP dependency | Add `spring-boot-starter-aop` directly to `wallet-service/pom.xml`. It is currently available only through `common-util`; wallet should explicitly own the runtime capability it enables. |
| Proxy enablement | Add `@EnableAspectJAutoProxy` to `WalletServer`, matching ledger. This activates the existing repository and `DbTxnExecutor` pointcuts. |
| Exception behavior | Reuse `DbExceptionTranslator` unchanged. Preserve an existing `DbException`, translate only recognized DB/transaction failures, and rethrow unclassified exceptions unchanged. Repository translation occurs first; transaction translation must not wrap the resulting `DbException` again. |
| Metrics | Keep the existing `MybatisStatementMetricsInterceptor` and `MeteredDbTxnExecutor`. Repository AOP adds no timer, so statement and full-transaction measurements remain distinct and are not duplicated. |
| Tests | Add a wallet Spring-context test proving repository and `DbTxnExecutor` beans are proxied and representative failures become the expected `DbException`. Verify existing `DbException` identity is preserved and DB timer beans remain active. |

### Explicitly Unchanged

- No repository, mapper, processor, SQL, transaction boundary, retry policy, or error-code change.
- No common-util implementation change; its pointcuts and conservative exception classification are
  already shared and covered by common-util unit tests.
- Do not add wallet-specific DB timers or exception types.

## 3. Metric Change Scope

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

## 4. Supporting Changes and Tests

- Add unit tests for reply success, reply error, malformed payload, retryable DB failure,
  unexpected failure, duplicate completion, retry exhaustion, and ack-failure recovery.
- Add configuration tests for retry thresholds and the retry-topic container factory.
- Add metric tests using `SimpleMeterRegistry`; assert exact counts and that active gauges return to
  zero.
- Add an Embedded Kafka test later for retry-topic/DLT routing. Unit tests alone cannot prove Spring
  topic routing.

## 5. Explicitly Excluded or No Change

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
