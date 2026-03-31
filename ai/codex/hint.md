## Project Invariants & Design Hints

- Wallet owns user-facing balances, reservations, and transaction intent; ledger owns accounting records; common-util provides shared infrastructure only.
- Wallet-ledger consistency is eventual by design; avoid synchronous settlement coupling or shared cross-service transactions.
- Every cross-service message must be atomically coupled with local state through an outbox.
- Ledger is a passive double-entry journal; per-asset debits and credits must net to zero.
- Reservation conservation must always hold: total = remaining + pendingSettle + consumed + released.
- Reserved-fund consumption or release must originate from a reservation lifecycle, never ad hoc balance mutation.
- Monetary safety belongs in DB transactions and atomic predicates, not application-only balance checks.
- DB is the source of truth; Redis is a fast gate/cache, Kafka is transport, neither defines committed outcome.
- Idempotency is layered and mandatory; retries or duplicate deliveries must never create duplicate business effects.
- Cache is optional acceleration: on failure fall back to DB; mutable records require versioned/CAS writes.
- Do not move wallet business rules into ledger, or ledger accounting rules into wallet.
- Never use floating-point money or publish cross-service events directly from business logic.
