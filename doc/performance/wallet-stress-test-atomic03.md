# Wallet Stress Test Diagnosis (`atomic03`)

## Test scope

- Operation: `atomic`
- Profile: `discovery`
- Wallets: `1,000`; duplicates: `5%`
- Load: `20 req/s` warm-up, then `+30 req/s` every `90 s` for eight stages
- Database and cache were truncated before each run.

## First test — before the index fix

### What happened

- Atomic gRPC traffic increased normally, but `complete_ledger_transaction` fell below 10 TPS.
- Completion throughput did not recover promptly after incoming traffic stopped.
- Business and Kafka-listener processing latency remained high, indicating a persistent backlog or downstream bottleneck.

![First test: wallet throughput and processing latency](assets/wallet-stress-atomic03/first-wallet-throughput.png)

![First test: Kafka listener outcomes and processing latency](assets/wallet-stress-atomic03/first-kafka-listener.png)

### Where was the problem?

The primary bottleneck was the wallet-reservation lookup:

- The table had an index on `(initiator, reference_id)`.
- `WalletReservationMapper.selectList` filtered only by `reference_id`.
- Because the leading index column was absent, MySQL could not efficiently seek through that index.

A second index/query mismatch existed in the outbox polling query. Commit `5faac0bc9f1d89a965f39e3cba757ecc17160819` aligned both query patterns with their indexes.

![First test: database and Hikari metrics](assets/wallet-stress-atomic03/first-db-latency.png)

![First test: infrastructure CPU saturation](assets/wallet-stress-atomic03/first-infra-cpu.png)

### Diagnosis, step by step

1. Correlate incoming TPS, completion TPS and business latency on one timeline.
2. Stop the load and observe recovery. No immediate recovery means work is still backlogged; it does **not** by itself rule out saturation.
3. Check Kafka publisher latency, listener latency and consumer lag separately. High listener processing latency can include business and database time, so it does not prove a Kafka broker problem.
4. Break down database latency by mapper and operation. `WalletReservationMapper.selectList` was the dominant slow operation.
5. Compare the captured SQL predicate with the table indexes and confirm the access path with `EXPLAIN ANALYZE` or slow-query statistics.
6. Use Hikari and host CPU as supporting evidence. They show resource pressure, but are not sufficient alone to identify the slow query.
7. Apply the query/index fix and repeat the same load profile with the same dataset and schema.

### Conclusion and next step

The first test was limited primarily by an unindexed wallet-reservation access path, with an additional outbox index mismatch. Deploy commit `5faac0b`, apply the required schema migration, and rerun the identical test to verify lower query latency and normal backlog recovery.

## Second test — after the index fix

### What happened

- Database operation latency was substantially lower than in the first test.
- Completion throughput reached about 120 TPS, then decreased as gRPC throughput continued toward approximately 268 TPS.
- When gRPC traffic stopped, completion throughput recovered to roughly 140–170 TPS and drained the backlog.

![Second test: completion TPS falls under gRPC load and recovers afterward](assets/wallet-stress-atomic03/second-wallet-throughput.png)

### Is the previous problem solved?

Yes, the evidence strongly indicates that the original slow-query problem is solved:

- DB operation latency remains mostly in the millisecond-to-tens-of-milliseconds range.
- The previous prolonged mapper-latency pattern is absent.
- Completion throughput recovers quickly after foreground traffic stops, unlike the first test.

For strict proof, compare before/after query plans and latency percentiles under the same schema, data volume and load profile.

### Where is the new problem?

The immediate bottleneck is the wallet Hikari connection pool, not slow SQL execution:

- Wallet active connections reach the configured maximum of 12.
- Pending connection requests spike into the hundreds.
- Connection acquisition latency rises sharply, with a series approaching the 2-second timeout.
- DB transaction latency spikes while individual DB operations remain relatively fast.
- gRPC handlers and the Kafka completion listener share the constrained pool, so increasing foreground concurrency reduces completion capacity.

CPU reached about 81.5% and may contribute to contention, but the connection-pool metrics provide the more direct explanation.

![Second test: wallet connection-pool saturation](assets/wallet-stress-atomic03/second-db-pool.png)

![Second test: Kafka listener slows and then recovers](assets/wallet-stress-atomic03/second-kafka-listener.png)

![Second test: infrastructure CPU pressure](assets/wallet-stress-atomic03/second-infra-cpu.png)

### Diagnosis, step by step

1. Align the wallet, Kafka, Hikari, database and host metrics to the same timestamps.
2. Verify that SQL operation latency stays low; this separates query execution from connection waiting.
3. Check Hikari active, idle, pending, acquisition latency and timeout metrics together.
4. Correlate the pending spike with the falling completion TPS and rising listener-processing latency.
5. Observe recovery after gRPC traffic stops. The completion burst indicates that the consumer is draining accumulated replies after connections become available.
6. Check MySQL CPU, active sessions and lock waits to ensure the pool limit—not database capacity—is the current constraint.

### Conclusion and next step

The index fix resolved the first bottleneck. The second run is limited by wallet DB connection-pool contention under combined gRPC and Kafka work.

Apply and test the following configuration:

```text
wallet maximumPoolSize = 32
wallet minimumIdle     = 16
MySQL max_connections  = 80
```

Restart the wallet service and recreate the MySQL container so the settings take effect. Rerun the same test and confirm:

- Hikari pending remains near zero;
- acquisition latency and connection timeouts remain low;
- completion TPS does not collapse as gRPC TPS rises;
- Kafka consumer lag stays bounded;
- MySQL CPU, query latency and lock waits remain healthy.

If pending requests remain high with 32 connections, inspect connection holding time and transaction boundaries before increasing the pool again.
