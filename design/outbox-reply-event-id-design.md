# Outbox Reply Event ID Design

## Context

Wallet and ledger communicate through Kafka and outbox.

The wallet service creates wallet-side state first:

1. create/update wallet transaction
2. update reservation/balance into a pending ledger-post state
3. create wallet -> ledger outbox in the same DB transaction
4. publish the ledger command asynchronously

After ledger replies successfully, wallet finalizes the wallet transaction.

This means post-ledger is not a reversible optional step in the current model. Once wallet has created the post-ledger outbox, the transaction is expected to eventually reach ledger success and then wallet completion.

## Design Concern

When ledger consumes one wallet command, duplicate processing can happen:

1. Kafka redelivers the same message because the first listener execution inserted the reply outbox but failed before ack.
2. Two listener executions process the same message concurrently due to retry/rebalance/timing bugs.

Possible result:

- `T1` processes the command and gets a success reply.
- `T2` processes the same command and gets a failure reply.
- Both try to insert a reply outbox.

If request event id and reply event id are strictly one-to-one, only one reply outbox can exist for that request event.

The dangerous case is:

1. `T2` inserts a failure reply.
2. `T1` fails to insert the success reply because the one allowed reply id already exists.
3. Upstream receives only failure, even though business logic already succeeded.

That suppresses the success signal and can create misleading upstream behavior.

## Current Wallet Behavior

Current wallet behavior supports a more tolerant reply model:

- Wallet finalizes only when post-ledger succeeds.
- Wallet does not currently close/reject the transaction when post-ledger fails.
- A failed post-ledger attempt leaves the transaction pending/stuck for retry or recovery.
- A later success reply can still complete the transaction correctly.

Therefore, a failure reply should not suppress a later success reply for the same original request event.

## Recommended Rule

Use this rule:

```text
one request event_id -> N possible deterministic reply event_ids
one reply intent -> one stable reply event_id
```

Do not use:

```text
one request event_id -> exactly one reply event_id
```

Also do not use arbitrary sequence-only reply ids for the same reply intent, because exact duplicate processing should still be idempotent.

## Why One-To-One Message Replies Are Not Enough

Even a perfect one-to-one request/reply mapping at the message level cannot prevent multiple reply outcomes for the same business command.

Example:

```text
request event_id = event-A, command_id = command-X -> failure reply
request event_id = event-B, command_id = command-X -> success reply
```

This can happen when upstream retries the same business command and creates a new Kafka message with a different `event_id` but the same `command_id`.

Therefore, strict one-to-one reply mapping for a single request event only prevents mixed replies from one duplicate message. It does not prevent mixed replies from duplicated business attempts.

The real consistency boundary must be business state, not message cardinality:

```text
wallet business state decides which reply can take effect
```

This means the wallet should treat these two situations similarly:

1. same request `event_id`, multiple reply intents
2. different request `event_id`, same `command_id`, multiple reply intents

Both are multiple signals about the same business transaction. Correctness depends on idempotent and guarded wallet state transitions.

## Reply Event ID Shape

Derive reply event id from:

- source request event id
- reply outcome
- stable reply facts

Example:

```text
request_event_id = wallet-event-123

success reply:
reply_event_id = reply:wallet-event-123:success

failure reply:
reply_event_id = reply:wallet-event-123:error:{namespace}:{errorCode}
```

This gives three useful properties:

1. Duplicate processing of the same success reply inserts the same reply event id and can be treated as idempotent success.
2. Duplicate processing of the same failure reply inserts the same reply event id and can be treated as idempotent success.
3. A success reply is not suppressed by an earlier failure reply for the same request event.

## Insert Conflict Handling

When reply outbox insert returns duplicate:

1. Load existing outbox by `event_id`.
2. Compare stable intent fields:
   - `event_type`
   - `event_id`
   - `command_id`
   - `destination`
   - `partition_key`
   - `payload` or payload hash
3. If same intent: ack as idempotent success.
4. If missing after duplicate: treat as ambiguous DB state and retry.
5. If conflicting intent: treat as data/contract conflict and DLQ.

Do not compare lifecycle fields such as:

- `outbox_status`
- `attempt_count`
- `last_attempt_at`
- `next_attempt_at`
- `last_error`
- `finalized_at`

Those may legitimately change after the first insert.

## Upstream Handling

Wallet should treat ledger failure replies as non-terminal unless the product introduces an explicit cancellable/compensatable state.

Current intended behavior:

- failure reply: transaction remains pending or retryable
- success reply: transaction completes if still pending
- late success after already completed: idempotent no-op
- late success after future compensated/closed state: no-op or reconciliation, not blind completion

If compensation is added later, wallet finalization must remain state-guarded:

```text
complete only when txn_status = PENDING / WAITING_LEDGER
do not complete when txn_status = CLOSED / COMPENSATED
```

If a future compensation/cancel flow is added, it should use the same rule: compensation and normal completion must be mutually guarded by DB state transitions. Only one path can win. Late replies from the other path should no-op or enter reconciliation, not blindly mutate wallet state.

## Metric Impact

One request event may produce more than one reply event, so reply event count is not the same as completed business transaction count.

Metrics should distinguish:

- reply outbox inserted count
- reply outcome count
- wallet transaction completion count
- duplicate same-intent outbox insert count
- conflicting outbox insert count

Completion correctness should be measured from wallet transaction state, not reply event volume.

## Final Decision

Use deterministic one-to-N reply event ids:

```text
request event -> multiple possible reply intents
reply intent -> one deterministic outbox event id
```

This avoids suppressing a later success reply while still deduplicating exact duplicate processing attempts.

One-to-N reply events do not introduce a fundamentally new consistency problem. Multiple reply outcomes can already happen through duplicated business attempts with different event ids and the same command id. The system must already rely on wallet-side business state guards for correctness.
