# Outbox Claim Index

## Decision

Outbox retry workers claim pending events in oldest-due-first order. Both wallet and ledger outbox tables use:

```sql
KEY idx_outbox_claim (outbox_status, next_attempt_at, id)
```

The claim query uses the matching order and an exclusive tuple cursor:

```sql
WHERE outbox_status = :pending
  AND attempt_count < :max_retries
  AND next_attempt_at <= :right_boundary
  AND (
       next_attempt_at > :last_next_attempt_at
       OR (next_attempt_at = :last_next_attempt_at AND id > :last_id)
  )
ORDER BY next_attempt_at ASC, id ASC
LIMIT :page_limit
FOR UPDATE SKIP LOCKED
```

The cursor predicate is omitted for the first page.

## Rationale

- `outbox_status` selects the active queue state.
- `next_attempt_at` is both the due-time filter and the leading sort key. It lets InnoDB scan due rows in index order without a filesort.
- `id` makes ordering and pagination deterministic when multiple events have the same attempt time.
- Oldest-due-first processing prevents overdue events from starving when retry capacity is temporarily saturated.
- A fixed `right_boundary` is captured once per retry cycle. Newer work is left for the next cycle instead of extending the current scan indefinitely.

## Pagination and scan range

The query uses keyset pagination instead of `OFFSET`. After a page ends at `(last_next_attempt_at, last_id)`, the next page starts strictly after that tuple. The `id` tie-breaker prevents both duplicate reads and skipped rows when several events share the same `next_attempt_at`. An `id`-only cursor is incorrect because `id` is not the leading sort key; it can skip a lower-id row whose attempt time belongs to a later page.

The index column order matches the equality filter, time range, and sort order:

```text
outbox_status = PENDING
next_attempt_at in (cursor time, right_boundary]
id > cursor id when next_attempt_at = cursor time
```

Consequently, every page can seek to the cursor inside the `PENDING` portion of the index and scan only the remaining due-time window up to `right_boundary`. It does not repeatedly scan and discard all rows from earlier pages as `OFFSET` pagination would. In contrast, `(outbox_status, id, next_attempt_at)` would make `id` the range key; a page could scan many `id > last_id` entries whose `next_attempt_at` is beyond the right boundary before rejecting them. Current `(last_next_attempt_at, last_id)` index is better since we do not want to scan rows whose `next_attempt_at` is beyond the right boundary (there could be many newly created rows with such `next_attempt_at`) in every page query.

The cursor is taken from the selected rows' **pre-claim** values. Claiming advances `next_attempt_at`, which moves those index entries; using their updated values would move the pagination boundary incorrectly.

## `SKIP LOCKED` and starvation

The selected rows are locked and claimed in the same transaction by advancing `next_attempt_at` and incrementing `attempt_count`. `FOR UPDATE SKIP LOCKED` lets concurrent workers claim different rows without waiting on one another.

There is one deliberate pagination trade-off: when a worker skips a locked row and then advances its cursor, it does not move backward to revisit that row during the same cycle. Normally the transaction holding the lock claims it. If that transaction rolls back, the row remains pending and is reconsidered in a later cycle.

Oldest-due-first ordering limits starvation under sustained backlog: each new cycle starts at the oldest eligible `next_attempt_at`, so overdue rows stay ahead of newer retries. `SKIP LOCKED` cannot guarantee that a row under a continuously held or repeatedly contended lock will never starve, but it prevents that row from blocking progress for the rest of the queue.

**TODO: Rows that exhaust `max_retries` should transition from `PENDING` to `DEAD`. Leaving them pending would make every cycle scan and reject old index entries using the non-indexed `attempt_count` predicate.**

## Existing database migration

Confirm the generated name of the old index with `SHOW INDEX FROM outbox`, then replace it in both service databases. For schemas created from the standard DDL, the old unnamed index is normally named `event_type`:

```sql
ALTER TABLE outbox
    DROP INDEX event_type,
    ADD INDEX idx_outbox_claim (outbox_status, next_attempt_at, id);
```

Verify representative backlog shapes with `EXPLAIN ANALYZE` and confirm that the claim query uses `idx_outbox_claim` without a filesort.
