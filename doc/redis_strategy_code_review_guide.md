# Redis Strategy Code Review Guide for Codex

## Purpose
This document is the **review baseline** for evaluating Redis-related code in the wallet / ledger services.
Codex should use this document as the source of truth when reviewing code that implements:
- cache read/write flows
- cache invalidation
- version CAS
- tombstone / negative cache
- client-side cache
- local hot-key routing
- TTL configuration
- idempotency key handling in Redis

The reviewer should not only check for syntax correctness. It must evaluate whether the implementation is **consistent with the intended cache strategy, concurrency model, and failure semantics** defined below.

---

## 1. Strategy classification

When reviewing code, first identify which Redis strategy the code is trying to implement.

### Strategy A: hot write + hot read path
Use for extremely hot mutable data, especially balance / snapshot style records.

**Expected design**
- DB is updated first inside the business transaction.
- Cache is refreshed **after commit** with the latest value.
- Cache write should use **version CAS** so older data cannot overwrite newer data.
- Normal writes should **not depend on tombstone** as the primary mechanism.
- Reads for promoted hot keys may use **L1 local cache + L2 Redis**.
- Hot-key routing may use a **local lazy promotion classifier**.
- Optional negative cache may exist for absent objects.

**Important implication**
This strategy is designed to avoid the stale cache-aside refill race on very hot mutable keys.

**Review focus**
- Is cache refreshed after commit instead of before commit?
- Is cache refresh based on the newest committed state?
- Is version CAS actually enforced?
- Is code incorrectly mixing this with delete-then-refill logic?
- Is local cache treated as optimization only, not correctness state?

---

### Strategy B: hot read, rare write path
Use for read-heavy data that changes infrequently.

**Expected design**
- Read path uses **cache-aside on miss**.
- Write path performs **post-commit delete** or **tombstone + CAS**.
- Tombstone is used when stale refill risk matters.
- Optional L1 local cache may exist for promoted hot keys.
- Negative cache may exist for miss protection.

**Review focus**
- On write, is cache invalidated only after DB commit?
- If stale refill risk matters, is tombstone or version watermark used?
- Is code accidentally allowing an old DB read to repopulate the cache after a newer write?
- If local cache exists, are TTLs kept short enough?

---

### Strategy C: normal mutable path
Use for ordinary mutable business data that is not extremely hot.

**Expected design**
- Read path uses cache-aside.
- Write path uses post-commit delete.
- Tombstone + CAS is only added when stale refill risk matters enough to justify complexity.
- Usually no special client-side cache path is needed.

**Review focus**
- Is the implementation simple and consistent?
- Is complexity reasonable for the heat of the data?
- Is code over-engineered with hot-path mechanisms for non-hot data?

---

### Strategy D: constant or nearly constant data
Use for data that rarely changes.

**Expected design**
- Long-lived cache-aside.
- Usually no tombstone.
- Usually no CAS.
- Manual invalidation or rare updates are acceptable.

**Review focus**
- Is the code avoiding unnecessary complexity?
- Is TTL long enough for stable data?
- Are updates still safe when they do happen?

---

## 2. Core design concepts Codex must understand

### 2.1 Version CAS
Cache values should carry a version. New writes must only succeed if the incoming version is newer than the existing one.

**Intent**
Prevent an older writer or stale refill from overwriting newer cache data.

**Review checks**
- Does cached value contain version information?
- Is write logic checking version ordering, not blindly overwriting?
- If Lua/script/CAS helper exists, does it compare correctly?
- Are equal-version semantics defined clearly?
- Is there any code path that bypasses version checks accidentally?

**Typical problem signs**
- Plain `set()` on mutable hot data without version discipline
- Refresh helper exists but some callers still overwrite directly
- Version generated inconsistently across entity lifecycle

---

### 2.2 Tombstone
A tombstone is a temporary marker stored in Redis instead of deleting the key outright.

**Intent**
Protect against the classic race:
1. reader misses cache
2. reader reads old DB value
3. writer commits newer DB value and deletes cache
4. reader writes old value back into cache

**Review checks**
- Is tombstone only used where stale refill risk justifies it?
- Does tombstone carry a version or watermark when required?
- Is tombstone TTL short but long enough to cover the refill race window?
- Does read path treat tombstone as an instruction to avoid stale refill?

**Typical problem signs**
- Tombstone exists but cache refill ignores it
- Tombstone TTL too short to cover the race window
- Tombstone used everywhere without need, adding complexity without benefit

---

### 2.3 Negative cache
Negative cache stores “not found” briefly.

**Intent**
Protect DB from repeated miss traffic.

**Review checks**
- Is negative cache TTL shorter than positive cache TTL?
- Is negative cache safe under replication lag / eventual consistency?
- Is negative cache avoided for cases where false negative would be dangerous?
- If read replica lag exists, is negative TTL kept very short?

**Typical problem signs**
- Long-lived negative cache on data that may appear shortly after write
- No distinction between true miss and temporary lag-induced miss

---

### 2.4 L1 local cache + L2 Redis
For very hot keys, local cache may sit in front of Redis.

**Intent**
Reduce network round trips and Redis load for hot read paths.

**Important principle**
L1 is an optimization only. It must not become correctness state.

**Review checks**
- Is local cache TTL short enough for mutable data?
- Is invalidation best-effort only, not treated as perfect correctness guarantee?
- Is there a separate Redis client / connection if server-assisted client-side cache is used?
- Is fallback to Redis correct when L1 misses or invalidates?

**Typical problem signs**
- Long L1 TTL on very hot mutable data
- Code assumes invalidation completely eliminates race windows
- Hot/local cache path changes semantics instead of only performance

---

### 2.5 Local lazy promotion classifier
A local positive-only set of known hot/system IDs or ref_ids routes selected keys to the L1+L2 path.

**Intent**
Apply client-side caching only to keys that are actually worth it.

**Expected behavior**
- Unknown key starts on normal cache path.
- If fetched object is identified as system/hot data, its id/ref_id is added to local hot set.
- Future reads route through L1+L2 path.
- No negative entries are stored.
- Separate namespaces should exist for `id` and `ref_id`.

**Review checks**
- Is the hot set used only as routing/performance hint?
- Is correctness independent from the hot set?
- Are id and ref_id handled separately?
- Does promotion happen lazily after positive discovery?
- Is no negative state stored locally?

**Typical problem signs**
- Hot set used as correctness source of truth
- Negative results cached in the classifier
- id and ref_id namespaces mixed

---

### 2.6 Separate tracked Redis connection
If Redis client-side caching / RESP3 tracking / Redisson local cached map is used, the tracked path should use a dedicated Redis connection.

**Review checks**
- Is tracked client separated from normal client?
- Is configuration explicit and intentional?
- Is tracked client only used for paths that benefit from it?

---

### 2.7 Distributed lock / herd protection
Hot-key refill may need singleflight or distributed locking.

**Intent**
Avoid thundering herd when hot keys expire or miss under heavy concurrency.

**Review checks**
- Is protection used only where needed?
- Is lock scope narrow enough?
- Is lock release safe?
- Is fallback behavior reasonable if lock acquisition fails?

---

## 3. TTL baseline and review expectations

TTL is mainly a **safety net**, not the primary correctness mechanism.
Correctness should come from:
- DB transaction boundaries
- post-commit cache mutation
- version CAS
- tombstone when required
- safe invalidation design

### 3.1 L1 local cache TTL
For mutable hot keys, keep it very short.

**Expected baseline**
- Very hot mutable keys: **200ms–1s**
- Recommended default starting point: **~500ms**
- Read-hot rare-write keys: **500ms–3s**

**Review concern**
If local TTL is too long, stale data may remain visible too long even with invalidation.

---

### 3.2 L2 Redis TTL
Redis TTL should usually be longer than L1 TTL.

**Expected baseline**
- Hot mutable key: **10s–60s**
- Normal mutable key: **30s–10min**
- Stable data: **hours–days**

**Review concern**
Redis TTL should not be the only freshness control for mutable data.

---

### 3.3 Negative cache TTL
**Expected baseline**
- Ordinary miss protection: **1s–30s**
- Lag-sensitive scenarios: **~100ms–1s**

**Review concern**
Long negative TTL may hide recently created data.

---

### 3.4 Tombstone TTL
Tombstone should last long enough to cover the stale refill race window, but not so long that it blocks legitimate refill too long.

**Expected baseline**
- Usually **seconds-level TTL**
- Exact value should reflect request latency / DB read latency / refill race window

**Review concern**
If too short, it fails to protect the race. If too long, it increases cold-miss cost and operational confusion.

---

### 3.5 TTL jitter
Non-trivial TTLs should usually include jitter.

**Expected baseline**
- Random jitter around **5%–20%** is a reasonable standard

**Review concern**
Without jitter, many keys may expire together and trigger stampedes.

---

## 4. Entity mapping guidance for wallet / ledger style systems

This section helps Codex infer whether an implementation matches the intended heat level of the entity.

### Likely Strategy A candidates
- Extremely hot `BalanceSnapshot`
- Any mutable snapshot read thousands of times and updated frequently

### Likely Strategy B candidates
- Read-heavy metadata with rare update
- Mapping objects queried frequently but changed rarely

### Likely Strategy C candidates
- Standard mutable transaction-supporting entities
- Reservation / transaction detail objects unless profiling proves them hot

### Likely Strategy D candidates
- Stable configuration
- Rarely changing system lookup data

**Review note**
The reviewer should challenge strategy mismatch. For example:
- If very hot snapshots are implemented with naive cache-aside + plain delete only, flag it.
- If cold data uses heavy client-side cache machinery, flag unnecessary complexity.

---

## 5. Read path review checklist

Codex should inspect each read path and answer the following:

1. What strategy is this read path implementing: A / B / C / D?
2. Is cache lookup order correct?
   - L1 -> L2 -> DB for hot promoted keys
   - L2 -> DB for normal keys
3. On miss, is DB refill safe under concurrency?
4. Does refill respect tombstone / version CAS when required?
5. Does negative cache behavior match business risk?
6. Is hot-key routing only a performance decision?
7. Are `id` and `ref_id` paths both handled consistently?
8. Is there protection against herd / repeated refill under load?

**Flag as bug or risk if**
- stale DB result can blindly overwrite newer cache
- read path ignores tombstone or version watermark
- local classifier changes correctness semantics
- `id` and `ref_id` are handled inconsistently

---

## 6. Write path review checklist

Codex should inspect each write path and answer the following:

1. Is DB write completed before cache mutation?
2. Is cache mutation done **after commit** rather than before commit?
3. Does the chosen cache mutation match the intended strategy?
   - Strategy A: refresh cache with latest committed value
   - Strategy B/C: delete or tombstone after commit
4. If using refresh, does it use version CAS?
5. If using delete, is stale refill risk acceptable or mitigated?
6. Are both primary key and ref_id cache entries handled consistently?
7. Are local and remote caches both considered?
8. If there are multiple cache keys for one entity, are they updated / invalidated atomically enough for the business requirement?

**Flag as bug or risk if**
- cache updated before DB commit
- some secondary cache key is forgotten
- ref_id key remains stale after id key refresh
- write path bypasses CAS helper

---

## 7. Client-side cache review checklist

If code uses Redisson local cache / Redis tracking / RESP3, Codex should verify:

1. Is tracked client separated from normal Redis client?
2. Is local cache TTL short enough for mutable hot keys?
3. Does code assume invalidation is perfect? If yes, flag as conceptual risk.
4. Is local cache only used for promoted hot/system keys?
5. Is fallback to Redis correct?
6. Are there separate local hot sets for id and ref_id?
7. Is no negative state stored in the hot classifier?

**Important conceptual rule**
Client-side cache is an optimization, not a correctness guarantee. Reviewers should flag any logic that depends on local invalidation being perfect.

---

## 8. Idempotency Redis review checklist

This is separate from business data caching but still part of Redis strategy.

### Expected design
Use token-based compare-and-delete for failure cleanup.

**Example state idea**
- `IN_PROGRESS|requestHash|ownerToken`
- `DONE|requestHash|responsePayload`

**Review checks**
1. Is the in-progress state written atomically?
2. Does cleanup use compare-and-delete with owner token rather than blind delete?
3. Are request hash mismatches handled clearly?
4. Is `IN_PROGRESS` TTL long enough for expected execution time?
5. Is `DONE` TTL long enough for replay / duplicate protection requirements?
6. Can one retry accidentally delete another request’s in-progress key? If yes, flag as bug.

**TTL baseline**
- `IN_PROGRESS`: usually **30s / 120s / 300s**, depending on business flow
- `DONE`: usually **10min+**, or longer if replay protection is important

---

## 9. Common bug patterns Codex should actively look for

### 9.1 Stale cache refill race
Reader loads old DB value and repopulates cache after a newer write committed.

**Usually caused by**
- naive cache-aside
- plain delete without tombstone/CAS on high-risk path
- direct cache set without version checks

---

### 9.2 Cache mutation before DB commit
If transaction rolls back, cache may expose uncommitted state.

---

### 9.3 Inconsistent multi-key invalidation
Object is cached by both `id` and `ref_id`, but only one key is updated or invalidated.

---

### 9.4 Treating local hot set as correctness state
Hot-key routing is only an optimization. If correctness depends on it, the design is wrong.

---

### 9.5 Overly long local TTL for mutable hot data
Even with invalidation, local stale visibility window may be too large.

---

### 9.6 Long negative cache under lag-sensitive reads
Recently created data may be hidden.

---

### 9.7 CAS helper exists but is bypassed
Part of the code path uses version-safe write, another path does plain overwrite.

---

### 9.8 Tombstone exists but refill logic ignores it
Design exists in theory but not in real control flow.

---

### 9.9 Unnecessary complexity for cold data
Tracked clients, local classifiers, version CAS, tombstones, and herd protection applied to data that does not need them.

---

## 10. Expected reviewer output format

When Codex reviews a change using this document, it should structure the result like this:

### A. Strategy identification
- Which entity / code path is being reviewed
- Which strategy (A/B/C/D) the implementation appears to target
- Whether that strategy choice is appropriate

### B. Logic flow summary
- Briefly summarize read flow
- Briefly summarize write flow
- Mention cache keys involved (`id`, `ref_id`, etc.)
- Mention whether L1/L2, tombstone, CAS, negative cache, or idempotency key logic is present

### C. Bugs
List concrete correctness bugs first.
For each bug, include:
- what is wrong
- why it is wrong
- the race / failure scenario
- suggested fix

### D. Risks / design mismatches
List non-fatal but important risks, such as:
- strategy mismatch
- TTL too long / too short
- incorrect assumptions about client-side invalidation
- unnecessary complexity

### E. Optimization suggestions
List improvements that are optional but valuable.

### F. Final verdict
State whether the implementation is:
- aligned with the intended Redis strategy
- partially aligned with important risks
- misaligned and should be redesigned

---

## 11. Strong review heuristics

Codex should apply these practical heuristics:

1. **Hot mutable snapshot + naive cache-aside only** -> likely wrong
2. **Cache write before commit** -> likely bug
3. **Multiple cache keys, only one invalidated** -> likely bug
4. **Client-side cache with long L1 TTL on mutable data** -> likely risk
5. **No version CAS on refresh-after-write hot path** -> likely risk or bug
6. **Hot-key classifier storing negatives** -> design bug
7. **Delete-only strategy on high-risk stale-refill path without tombstone/CAS** -> likely risk
8. **Negative cache too long where recent creates are expected** -> likely risk
9. **Blind delete of idempotency in-progress key** -> likely bug
10. **Cold data implemented with heavy hot-path machinery** -> likely overengineering

---

## 12. Final design summary Codex should remember

### Strategy A
Hot write + hot read:
- post-commit cache refresh
- version CAS
- no tombstone on normal writes
- L1 local + L2 Redis for promoted hot keys
- separate tracked connection
- short jittered TTL as safety net
- optional negative cache

### Strategy B
Hot read + rare write:
- cache-aside on miss
- post-commit delete or tombstone with CAS
- L1 local + L2 Redis for promoted hot keys
- separate tracked connection
- medium jittered TTL
- negative cache

### Strategy C
Normal mutable:
- cache-aside on miss
- post-commit delete
- tombstone + CAS only when stale refill risk matters

### Strategy D
Constant data:
- long-lived cache-aside
- usually no tombstone
- usually no CAS

### Cross-cutting rules
- TTL is a safety net, not the primary correctness mechanism.
- Local cache is optimization only, not correctness state.
- Hot-key classifier is a routing hint only.
- id and ref_id should be handled consistently.
- For idempotency cleanup, use token-based compare-and-delete, not blind delete.

