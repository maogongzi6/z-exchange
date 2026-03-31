# Analysis: `// TODO delete tombstone base on CAS`

## Conclusion

Under the current code, this TODO is not needed, and implementing it as an actual tombstone delete would likely be wrong.

The tombstone already serves its purpose without a delete:

- it preserves a version barrier after a DB update
- readers treat tombstone as a cache miss and reload from DB
- the first fresh DB read can overwrite the tombstone directly with the same version

Because of that, "delete tombstone based on CAS" is redundant at best, and can re-open the stale-write race at worst.

## Evidence from code

### 1. Tombstone is written after a successful metadata update, using the post-update version

- [`ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:129`](../../../ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java#L129) calls `ledgerTxnRepository.updateMetadataByPk(txn)`, then writes a tombstone with `txn.getVersion()` at line 135.
- [`ledger-service/src/main/java/com/exchange/app/ledger/dao/repository/LedgerTxnRepository.java:29`](../../../ledger-service/src/main/java/com/exchange/app/ledger/dao/repository/LedgerTxnRepository.java#L29) copies `toUpdate.getVersion()` back into `txn` after `mapper.updateById(toUpdate)` at line 38.
- [`ledger-service/src/main/java/com/exchange/app/ledger/po/ledger/LedgerTxn.java:25`](../../../ledger-service/src/main/java/com/exchange/app/ledger/po/ledger/LedgerTxn.java#L25) marks `version` with `@Version`.

This strongly suggests the tombstone is written with the new DB version, not the old one.

### 2. Tombstone is not treated as a valid cache hit

- [`common-util/src/main/java/com/exchange/common/redis/cache/model/CacheValueInfo.java:33`](../../../common-util/src/main/java/com/exchange/common/redis/cache/model/CacheValueInfo.java#L33) says `ifCacheHit(...)` returns `false` for `TOMBSTONE`.
- [`ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:39`](../../../ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java#L39) checks cache first in `getByTxnId(...)`.
- [`ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:48`](../../../ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java#L48) only returns early on `ifCacheHit(cacheInfo)`.
- [`ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:55`](../../../ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java#L55) falls through to DB when the cache entry is a tombstone.

So a tombstone is an invalidation marker, not a terminal negative result.

### 3. Fresh data already replaces tombstone through the existing CAS write path

- [`common-util/src/main/java/com/exchange/common/redis/cache/strategy/VersionCacheAsideAbstract.java:24`](../../../common-util/src/main/java/com/exchange/common/redis/cache/strategy/VersionCacheAsideAbstract.java#L24) writes tombstone via `versionedCacheRedisClient.setTombstone(...)`.
- [`common-util/src/main/java/com/exchange/common/redis/cache/strategy/VersionAbstract.java:41`](../../../common-util/src/main/java/com/exchange/common/redis/cache/strategy/VersionAbstract.java#L41) writes normal cache data via `setIfAbsentOrNewer(...)`.
- [`common-util/src/main/java/com/exchange/common/redis/cache/component/support/VersionedRedisSupport.java:62`](../../../common-util/src/main/java/com/exchange/common/redis/cache/component/support/VersionedRedisSupport.java#L62) shows tombstone and normal value both go through `doSetIfAbsentOrNewer(...)`.
- [`common-util/src/main/resources/script/lua/set-if-absent-or-newer.lua:26`](../../../common-util/src/main/resources/script/lua/set-if-absent-or-newer.lua#L26) updates Redis when `newVersion >= oldVersion`.

That `>=` is the key point: if Redis contains tombstone version `V`, then a later DB read of version `V` can write the real payload back immediately. No delete step is needed.

## Why deleting the tombstone is unsafe

Concrete race with current design:

1. Cache is empty, so reader `R` goes to DB and reads old row version `1`.
2. Writer `W` updates DB to version `2`.
3. `W` writes tombstone version `2`.
4. If some cleanup now deletes the tombstone, Redis becomes absent again.
5. `R` later calls `setCacheAside(..., newVersion=1)`.
6. [`set-if-absent-or-newer.lua:10`](../../../common-util/src/main/resources/script/lua/set-if-absent-or-newer.lua#L10) accepts the write because the key is absent.
7. Stale version `1` is back in cache.

The whole reason to keep tombstone is to preserve the version barrier long enough to reject that stale write. Deleting it removes the protection.

## Why the TODO likely exists anyway

The local git history shows an earlier version of the Lua used strict `>` instead of `>=`:

- commit `14331f1b` on 2026-03-20 changed `if newVersion > oldVersion then` to `if newVersion >= oldVersion then` in `set-if-absent-or-newer.lua`
- commit `a5ff809` on 2026-03-25 added the TODO in `VersionCacheAsideAbstract`

If the Lua still used strict `>`, then tombstone version `V` would block re-caching the same version `V`, and a follow-up idea like "delete tombstone based on CAS" would make conceptual sense.

But that is not the current code anymore. Current code already allows same-version replacement, so the TODO looks like a mistaken follow-up note.

## Additional supporting point

There is no version-aware delete primitive in the active versioned cache client:

- [`common-util/src/main/java/com/exchange/common/redis/cache/client/VersionCacheClient.java:29`](../../../common-util/src/main/java/com/exchange/common/redis/cache/client/VersionCacheClient.java#L29) exposes only a blind `delete(key)`

So even if someone tried to implement this TODO literally, the current shared client does not provide a safe CAS delete operation for normal tombstone flow.

## Assumptions

- I did not execute the update path. The statement that `txn.getVersion()` is the post-update version is inferred from `@Version` on `LedgerTxn.version` plus the repository copying `toUpdate.getVersion()` back into `txn`.
- I did not find any hidden compare-and-delete path in `common-util/src/main`; this conclusion assumes the active versioned cache flow is only the one traced above.
