# Outbox Listener Idempotent Insert Design Evaluation

## Verdict

The design direction is sound: duplicate outbox insertion must not be treated as automatically retriable, and Kafka listener retry should be explicit rather than "retry everything except configured exclusions".

The design is not complete yet. The largest missing piece is deterministic outbox identity. Current outbox `event_id` generation includes a sequence suffix, and current SQL only makes `event_id` unique while `command_id` is only indexed. With that shape, a duplicate command can create a new outbox row instead of returning `inserted = 0`, so the proposed verification logic will not reliably trigger.

## Outbox Insert Logic

Your proposed decision table is correct:

| Case | Decision | Kafka ack |
| --- | --- | --- |
| `inserted = 1` | success, try immediate publish | ack |
| `inserted = 0`, existing row is same intent | idempotent success | ack |
| `inserted = 0`, existing row missing | ambiguous DB state, retriable | do not ack |
| `inserted = 0`, existing row conflicts | contract/data conflict, non-retriable | DLQ |

This is the right model for at-least-once Kafka delivery. A duplicate delivery should be idempotent success if the durable outbox intent already exists.

Required correction: outbox identity must be deterministic.

Current state:

- Ledger `IdGenerator.generateEventId(commandId)` returns `commandId + "-" + sequence`.
- Wallet has the same sequence-based event id pattern.
- SQL has `unique key(event_id)` but only `key(command_id)`.
- `OutboxRepository.selectByCommandId` uses `selectOne`, but the schema does not guarantee one row per `command_id`.

This means a duplicate command can produce a different `event_id`, so `insertIgnore` may insert another row instead of returning `0`.

Recommended fix before implementing this logic:

1. Define the outbox idempotency key.
   Prefer deterministic `event_id = hash/source + command_id + event_type`, or add a unique key such as `(event_type, command_id)`.
2. Add repository lookup by the same unique key used by the insert.
3. Do not rely on non-unique `command_id` with `selectOne`.
4. Add tests for duplicate command delivery.

## Existing Intent Verification

The "same intent" comparison should compare stable intent fields only:

- `event_type`
- `event_id` or the deterministic unique key
- `command_id`
- `destination`
- `partition_key`
- `payload` bytes, or a deterministic payload hash

Do not compare lifecycle fields:

- `outbox_status`
- `attempt_count`
- `last_attempt_at`
- `next_attempt_at`
- `last_error`
- `finalized_at`

Those lifecycle fields can legitimately differ after the first insert.

Recommended common-util API:

```java
OutboxInsertDecision insertWithClaimAndVerify(Outbox intended, LocalDateTime nextAttemptAt)
```

Possible decision values:

```java
INSERTED
EXISTING_SAME_INTENT
EXISTING_MISSING_AMBIGUOUS
EXISTING_CONFLICT
```

This keeps duplicate handling out of each service listener.

## Exception Model

The proposed exception behavior is directionally correct, but should be made more explicit.

Recommended common exceptions:

- `KafkaListenerRetriableException`
  - for listener-level retry
  - should preserve the original cause
  - can wrap a `CustomizedException` whose error code implements `RetryableErrorCode`
- `KafkaListenerNonRetriableException`
  - for DLQ
  - should preserve the original cause
- `DataConflictException`
  - reasonable for outbox same-key/different-intent conflict
  - should be non-retriable
- `ProtoParseException`
  - better name than wrapping `InvalidProtocolBufferException` directly
  - non-retriable, because malformed bytes are deterministic poison messages

The existing `RetriableException` is usable, but it currently lacks constructors with `cause`. If listener retry wraps lower-level exceptions, add cause-preserving constructors.

## DefaultErrorHandler Policy

Your proposed direction is correct: retry only explicitly retriable listener exceptions.

Current common-util behavior is the inverse:

```java
errorHandler.addNotRetryableExceptions(customKafkaConfig.getNotRetriableExceptions());
```

That means unknown exceptions are retried by default. This is risky because bad proto data, invalid enum values, contract violations, and data conflicts can waste retry capacity before going to DLQ.

Recommended policy:

- retry only `KafkaListenerRetriableException` / `RetriableException`
- send all other exceptions to DLQ immediately
- do not expose `notRetriableExceptions` as service config for core behavior
- make retry classification centralized in common-util

The handler can still log the root cause and error code for observability.

## Common DefaultListener Shape

The proposed common listener is useful, but `Function<EventEnvelopePb, Result<Outbox>>` is too narrow for all services.

Ledger command listener:

- consumes wallet command
- creates ledger records
- creates reply outbox
- acks only after reply outbox is durable

Wallet reply listener:

- consumes ledger reply
- updates wallet state
- no reply outbox is needed

So common-util should probably provide either:

1. A specialized `OutboxReplyCommandListener` for handlers that must create a reply outbox.
2. A more general listener template returning a richer action:

```java
sealed interface ListenerAction {
    AckOnly ackOnly();
    OutboxReply outboxReply(Outbox outbox);
    Retry retry(ErrorCode errorCode, String detail);
    Dlq dlq(ErrorCode errorCode, String detail);
}
```

For the current phase, option 1 is simpler and safer if the target is ledger's command listener only.

Also, `replyPublisher` should not be `Consumer<Outbox>`. Use `IPublisher` or `Function<Outbox, Result<Void>>` so publish failures can be logged/metriced consistently. Since publish happens after ack, a publish failure must not trigger Kafka listener retry; the outbox retry worker owns recovery after this point.

## Refactored Ledger Listener

The proposed ledger-service cleanup is good:

- parse `EventEnvelopePb`
- route by `event_type`
- parse concrete payload
- call business handler
- create failure reply outbox for business/non-retryable result
- return retry result or throw retriable listener exception for explicit retryable infra failure

Recommended corrections:

- Default event-type branch should throw `InvalidEnumException`, not `AbnormalProtoDataException`.
- Payload parse failure should be wrapped in `ProtoParseException`, not rethrown as `InvalidProtocolBufferException`.
- Do not use protobuf error fields for listener control flow after payload parsing.
- Do not publish `internal` reply outbox for transient infra failure unless the product contract explicitly treats it as a terminal result.

## Business Retry Question

For retryable business-flow errors such as DB unavailable, use bounded Kafka listener retry if no durable local outcome or reply outbox has been created yet.

Recommended ownership:

1. Component-level retry:
   - very short retry around low-level transient operations
   - useful for deadlock, lock wait timeout, brief pool hiccup
2. Kafka listener retry:
   - bounded retry for the whole message
   - only for explicit retriable failures before ack
   - do not ack while no durable outcome exists
3. Upstream stuck-transaction recovery:
   - long-horizon recovery
   - retries or reconciles transactions that did not receive a reply

These layers are not redundant if each layer has a different time horizon. The risky design is unlimited or broad listener retry.

Do not convert DB unavailable into a normal outbox reply with `internal` by default. That can make upstream treat a transient infrastructure failure as a terminal business result. Prefer:

- short component retry
- bounded listener retry
- DLQ and alert after retries are exhausted
- upstream recovery can re-drive the command using the same idempotency key/command id

## Missing Metrics And Observability

When implementing this, add metrics for the new decision points:

- outbox insert decision: inserted, existing_same_intent, missing_ambiguous, conflict
- listener outcome: success, retry, dlq
- listener error type: proto_parse, invalid_enum, outbox_ambiguous, outbox_conflict, db_unavailable, db_timeout
- immediate publish result after ack: success, failure
- DLQ publish count

These metrics will be useful when validating whether duplicate delivery is idempotent or producing conflicts.

## Final Recommendation

Implement in this order:

1. Make outbox identity deterministic or add a unique key matching the intended idempotency key.
2. Add `OutboxRepository.insertWithClaimAndVerify`.
3. Add explicit listener retry/non-retry exception classes or update `RetriableException` with cause support.
4. Change `DefaultErrorHandler` to retry only explicit retriable exceptions.
5. Refactor ledger listener to use the common outbox-reply listener template.
6. Refactor wallet listener later with a separate ack-only template if needed.

The concept is solid, but deterministic outbox identity is a prerequisite. Without that, `inserted = 0` will not reliably represent duplicate delivery.
