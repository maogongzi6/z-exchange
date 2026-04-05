# Cache Design Evaluation

## 1. Overall score
- Score: 6/10

## 2. Overall verdict
- Verdict: not bad

## 3. Suggested engineer level
- Level: mid

## 4. Scope reviewed
- Reviewed doc:
  - `doc/redis_strategy_code_review_guide.md`
- Reviewed cache framework under `common-util/src/main/java/com/exchange/common/redis`:
  - `BaseRedisSupport`
  - `BaseCacheReadSupport`
  - `BaseClientSideCacheSupport`
  - `register/BaseRegister`
  - `register/RedissonRegister`
  - `config/RedissonProperties`
  - `cache/component/support/ReadableCache`
  - `cache/component/support/DefaultReadableCache`
  - `cache/component/support/SimpleCache`
  - `cache/component/support/VersionCache`
  - `cache/component/support/RawDeletableCache`
  - `cache/component/codec/*`
  - `cache/component/factory/ReadableCacheFactory`
  - `cache/constant/CacheType`
  - `cache/model/CacheValueInfo`
  - `cache/model/StrategyOption`
  - `cache/ops/*`
  - `cache/ops/impl/*`
  - `cache/strategy/*`
  - `cache/strategy/impl/*`
  - `cache/register/*`
  - `common-util/src/main/resources/script/lua/set-if-absent-or-newer.lua`
- Reviewed reference usages:
  - `wallet-service/src/main/java/com/exchange/app/wallet/processor/balance/GetBalanceSnapshotProcessor.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/dao/store/BalanceSnapshotStore.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/WalletCacheRegister.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/register/PromotionClassifierRegister.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/constant/cache/CacheTtlStrategies.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/constant/cache/CacheScope.java`
  - `wallet-service/src/main/java/com/exchange/app/wallet/config/CustomCacheProperties.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/processor/post/GetLedgerTxnProcessor.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/dao/cache/LedgerRegister.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/constant/cache/CacheTtlStrategies.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/constant/cache/CacheScope.java`
  - `ledger-service/src/main/java/com/exchange/app/ledger/config/CustomCacheProperties.java`
- Reviewed classifier abstraction used by hot routing:
  - `common-util/src/main/java/com/exchange/common/component/PromotionClassifier.java`
  - `common-util/src/main/java/com/exchange/common/component/DefaultPromotionClassifier.java`
- Intentionally excluded:
  - `common-util/src/main/java/com/exchange/common/redis/idemp`
  - unfinished business-flow questions such as incomplete snapshot write-side cache update and exact TTL tuning
- Important uncertainty:
  - I did not find concrete `application.yml` / `application.properties` values for the reviewed TTLs in this repo, so exact runtime TTL adequacy cannot be scored precisely.

## 5. Design summary
- The cache design is built around three layers:
  - low-level Redis access and client-side cache plumbing in `BaseRedisSupport`, `BaseClientSideCacheSupport`, and `RedissonRegister`
  - encoding and primitive cache components in `SimpleCache`, `VersionCache`, `ValueCodec`, and `VersionCodec`
  - strategy composition in `DefaultStableCacheStrategy`, `DefaultVersionCacheAsideStrategy`, and `DefaultVersionPostRefreshStrategy`
- `VersionCache` is the main mutable-data primitive. It stores a cache payload together with `CacheType` and version, and writes through a Redis Lua CAS script (`set-if-absent-or-newer.lua`).
- `SimpleCache` is the stable-data primitive. It stores plain typed payloads or a negative marker without versioning.
- `StrategyOption` controls whether a versioned strategy uses tombstone and negative-cache behavior, while TTLs are injected through `TtlStrategy`.
- In the current usages:
  - `BalanceSnapshotStore.hotSnapshotCache` is intended to model Strategy A: hot mutable data, post-refresh write semantics, Redis client-side cache on the read path, version CAS on writes.
  - `BalanceSnapshotStore.normalSnapshotCache` is intended to model Strategy C with extra stale-refill protection: cache-aside read path, tombstone on update, version CAS on refill.
  - `LedgerTxnStore.ledgerTxnCache` is also Strategy C, but implemented with version CAS and tombstone even though the code comments describe it as a deliberate tech drill.
  - `balanceSnapshotRefCache` and `ledgerRefCache` are closest to Strategy D: stable index caches with optional short negative-cache protection.

## 6. What is working well
- The design is built around the right concepts. Version CAS, tombstone, negative cache, separate tracked Redis connection, and local hot-key routing all match the strategy guide and show that the author is thinking in terms of concurrency and failure semantics rather than raw Redis CRUD.
- `VersionCache` is a solid core primitive. Putting version, cache type, and payload into one encoded value gives the framework one place to model normal values, tombstones, and negatives, and the Lua CAS script makes the main monotonicity check happen inside Redis rather than in racy application code.
- The separation between transport, codec, ops, and strategy is directionally good. `DefaultReadableCache`, `VersionWriteOpsImpl`, `VersionTombstoneOpsImpl`, and the strategy classes each have a narrow job. That makes the intended invariants easier to see than if everything lived inside one large store class.
- The design recognizes that client-side cache is different infrastructure, not just a flag on the normal client. `RedissonRegister` creates a dedicated RESP3-based tracked client for app-side cache reads, which is exactly the right architectural direction for Strategy A.
- `CacheDescriptor` is a good small abstraction. It keeps cache key construction and target class binding out of the strategy implementations and makes store assembly explicit.
- TTL jitter is modeled centrally through `TtlStrategy`, which is the right instinct. The framework treats TTL as a policy input rather than burying literals inside cache call sites.

## 7. Main design concerns
- The strongest design flaw is that the encoding contract is not protected by the abstraction boundary. `WalletCacheRegister.hotSnapshotCache()` reads through `clientSideReadableCache`, but `ReadableCacheRegister.clientSideReadableCache()` is built with `ValueCodec`, while `VersionCache` writes snapshot values with `VersionCodec`. That means the same `ReadableCache` interface can be wired to a decoder that cannot parse the stored format. This is not just a bug in one bean; it shows the type system is too weak to preserve a core cache invariant.
- The strategy layer is conceptually good but mechanically fragile. Strategy behavior is split across `DefaultVersionCacheAsideStrategy`, `DefaultVersionPostRefreshStrategy`, and `StrategyOption` booleans. Because the feature matrix is represented by booleans and duplicated class structure, it is easy to invert semantics or leave gaps. The current code already shows that risk: `requireNegativeCache` handling in both versioned strategy implementations is backwards.
- Version discipline is not end-to-end. The main write path uses version CAS, but `VersionNegativeCacheOpsImpl.cleanNegative()` falls back to raw delete. That leaks out of the versioned model and means the framework does not consistently enforce its own ordering rules once negative markers are involved.
- The design depends heavily on assembly correctness in service-specific config classes. `WalletCacheRegister` and `LedgerRegister` manually compose multiple small ops beans, codecs, readers, and strategies. That makes the framework flexible, but it also means correctness depends on every caller picking the right reader/codec/strategy combination. The hot snapshot miswiring shows this is currently too easy to get wrong.
- The abstraction surface is not yet fully coherent. `VersionCacheStrategy` is lifecycle-oriented (`afterDbHit`, `afterDbMiss`, `afterInsert`, `afterUpdate`), while `StableCacheStrategy` exposes direct cache operations. That asymmetry makes the framework harder to reason about as one system. It also means new strategies will likely need more special-case wiring instead of plugging into one uniform model.
- The design has weak executable protection. I did not find non-idempotency tests covering codec compatibility, CAS semantics, tombstone behavior, negative-cache behavior, or tracked-client reads. With this many invariant-heavy moving parts, that is a serious architecture risk, not just a test gap.

## 8. Strategy alignment review
- Strategy A: hot write + hot read path
  - Guide expectation:
    - post-commit refresh with latest value
    - version CAS
    - L1 local + L2 Redis for promoted hot keys
    - local promotion as routing hint only
  - Code observed:
    - `WalletCacheRegister.hotSnapshotCache()` uses `DefaultVersionPostRefreshStrategy`
    - write side goes through `VersionWriteOpsImpl` and `VersionCache.setIfAbsentOrNewer(...)`
    - tracked client infrastructure exists in `RedissonRegister`
    - promotion abstraction exists through `PromotionClassifier`
  - Alignment: partial
  - Main mismatch or uncertainty:
    - the hot read path is not assembled safely because `clientSideReadableCache` is non-versioned while hot snapshot values are versioned
    - the framework direction matches Strategy A, but the actual abstraction contract does not make that path safe
- Strategy C: normal mutable path
  - Guide expectation:
    - cache-aside read path
    - post-commit delete or tombstone when stale-refill risk matters
    - complexity should stay reasonable
  - Code observed:
    - `DefaultVersionCacheAsideStrategy` implements cache-aside refill on `afterDbHit`
    - `afterUpdate` writes tombstone through `VersionTombstoneOpsImpl`
    - ledger and normal snapshot both use version CAS and tombstone
  - Alignment: strong for intent, partial for execution
  - Main mismatch or uncertainty:
    - the negative-cache option handling is implemented backwards
    - equal-version overwrite semantics in the Lua script are permissive and not clearly documented
    - raw delete for negative cleanup weakens the versioned model
- Strategy D: constant or nearly constant data
  - Guide expectation:
    - simple long-lived cache-aside
    - little or no tombstone/CAS complexity
  - Code observed:
    - `DefaultStableCacheStrategy` + `SimpleCache` are used for ref caches
    - negative cache is optional at the caller and index keys are stored directly
  - Alignment: strong
  - Main mismatch or uncertainty:
    - consistency with the corresponding entity cache remains manual in store code rather than protected by a higher-level abstraction

## 9. Runtime/system-behavior review
- Cache miss
  - Normal mutable path is conceptually sound: read cache, fall back to DB, then refill via `afterDbHit(...)` or `afterDbMiss(...)`.
  - Stable ref cache path is also straightforward.
  - Hot snapshot path is unsafe as currently assembled because a tracked read can receive a versioned value that `ValueCodec` cannot decode.
- Stale data risk
  - Strategy C paths have a reasonable stale-refill defense story: version CAS plus tombstone is the right toolkit.
  - Strategy A has the right intended story but the current framework assembly does not make the read side trustworthy enough to call it strong.
  - Raw delete in `VersionNegativeCacheOpsImpl.cleanNegative()` is a correctness boundary leak.
- Concurrent reads/writes
  - The Lua CAS script is the main strong point here and materially improves concurrency behavior.
  - There is no herd protection or singleflight for refill. That may be acceptable now, but under hot-key expiry or cold-start bursts the framework will still stampede DB/Redis.
  - The Lua script uses `newVersion >= oldVersion`, so equal-version overwrites are allowed. That may be acceptable if equal version means same state, but the framework does not enforce or document that invariant.
- Future client-side cache / hot-key expansion
  - The infrastructure direction is good: separate tracked client, promotion classifier, dedicated hot strategy.
  - The framework is not yet safe to extend because reader type and encoding type are not coupled. Adding more hot caches would repeat the same wiring risk unless the API becomes more typed.
  - `PromotionClassifier` is intentionally simple and positive-only, which is good, but it is also just a raw `String` set. Namespace discipline and growth limits are left entirely to callers.
- TTL expiration / refill behavior
  - TTL jitter exists, which is good.
  - Exact TTL quality is uncertain because config values were not present in the repo.
  - The framework models Redis TTLs, but the client-side cache TTL is configured globally in `RedissonProperties` rather than alongside the strategy definition. That weakens per-strategy reasoning about stale-window behavior.
- Ref cache and entity cache consistency expectations
  - The system treats ref caches as separate stable indexes pointing to entity IDs.
  - That is a reasonable model, but consistency is maintained manually in each store. There is no framework-level multi-key mutation abstraction, so correctness across entity and ref keys depends on domain code discipline.

## 10. Abstraction and extensibility review
- The current abstraction is directionally clean but not yet robust.
- What is clean:
  - separation of key descriptor, codec, raw cache primitive, op adapters, and high-level strategy
  - explicit wiring of hot vs normal vs stable paths
  - reusable `VersionCache` primitive for all versioned entity caches
- What is not clean enough:
  - `ReadableCache` is too generic. It hides whether the underlying data is versioned or not, which is how the hot snapshot path ended up using the wrong decoder.
  - `VersionCacheStrategy` and `StableCacheStrategy` do not form one coherent family. One is lifecycle-based; the other is direct-operation-based.
  - `StrategyOption` uses booleans for behavior switches. That keeps the API small, but it also makes illegal or inverted combinations easy.
  - strategy-specific guarantees are not encoded in types. The caller cannot tell from the signature whether a strategy performs delete, tombstone, refresh, or CAS-cleanup without reading the implementation.
- Adding new cache strategies will remain manageable only if the framework becomes more typed and more declarative. In its current form, every new strategy risks adding another service-side assembly puzzle instead of a safe reusable building block.
- Responsibilities are mostly separated, but too much correctness still lives in bean wiring. That is the main extensibility risk.

## 11. Strongest design decisions
- Using versioned cache values with Redis-side CAS is the strongest decision. It directly addresses stale overwrite races instead of relying on timing luck.
- Modeling tombstone and negative cache as first-class cache states through `CacheType` is strong. It keeps the state machine explicit.
- Creating a separate tracked Redisson client for app-side cache use is a mature design choice and aligns with the guide.
- Keeping cache key construction in `CacheDescriptor` is a good separation point and reduces key-format duplication in strategy code.
- Using domain stores (`BalanceSnapshotStore`, `LedgerTxnStore`) as explicit composition points keeps business policy out of the lower-level Redis primitives.

## 12. Weakest design decisions
- Exposing all read paths behind the same `ReadableCache` interface is the weakest decision. It allows versioned and non-versioned caches to be mixed accidentally.
- Encoding behavior switches as booleans in `StrategyOption` is too error-prone for a framework built around correctness-sensitive cache semantics.
- Allowing raw delete inside a versioned strategy path breaks the main monotonicity story.
- Leaving hot-path reader assembly to ad hoc bean composition makes Strategy A harder to use safely than it should be.
- Having no framework tests for codec/strategy invariants leaves too much confidence resting on manual inspection.

## 13. Signs of mature engineering judgment
- The design clearly recognizes that hot mutable data needs a different strategy from normal mutable data and stable index data.
- Redis client-side cache is treated as separate infrastructure with a dedicated client, not as a magical free optimization.
- The author understands the stale-refill race and is trying to solve it with CAS and tombstone rather than only TTL.
- Negative cache and tombstone are modeled separately, which is conceptually correct.
- TTL jitter is included from the start, which shows awareness of expiry clustering.

## 14. Signs of over-design or unnecessary complexity
- `LedgerTxnStore` uses version CAS and tombstone even though the code comments describe ledger transactions as a low-heat mutable path and say the versioned design is being used partly as a tech drill. That is acceptable for learning, but it means the framework is not yet calibrated tightly to business heat.
- The framework has several layers of wrappers (`ReadableCache`, raw cache support, op adapters, strategy classes, factory) without the type safety needed to justify that level of composition.
- There are commented-out client-side cache classes (`VersionAppSideCacheReadClient`, `L1L2ReadOpsImpl`, old client code) that suggest the design is still in churn rather than fully settled.

## 15. The 3 highest-leverage design improvements
- 1. Make versioned vs non-versioned read paths type-safe.
  - Why it matters:
    - This is the highest-value fix because it closes the biggest design hole: the framework currently allows a cache reader with the wrong decoder to be injected into a versioned hot path.
  - What it would improve:
    - safer Strategy A expansion
    - fewer wiring mistakes
    - much stronger abstraction boundaries
  - Concrete direction:
    - expose separate interfaces or builders for versioned readable caches and stable readable caches, or parameterize the reader on the codec family so miswiring is impossible
- 2. Replace boolean strategy switches with explicit strategy descriptors or capability types.
  - Why it matters:
    - `requireTombstone` and `requireNegativeCache` are too easy to implement incorrectly, and the current code already demonstrates that.
  - What it would improve:
    - clearer invariants
    - safer future strategy additions
    - easier testing of the allowed behavior matrix
  - Concrete direction:
    - model Strategy A / C / D as explicit types or enums with dedicated constructors, or define capability objects such as `NegativeCachePolicy`, `WriteMutationPolicy`, and `RefillPolicy`
- 3. Enforce version discipline end-to-end and back it with focused framework tests.
  - Why it matters:
    - the design’s value comes from invariants, not from API shape alone
  - What it would improve:
    - confidence in stale-refill protection
    - safer negative/tombstone handling
    - faster future refactors
  - Concrete direction:
    - remove raw-delete escape hatches from versioned paths where possible
    - define and test equal-version semantics explicitly
    - add framework tests for versioned decode/read compatibility, CAS ordering, tombstone refill blocking, negative-cache behavior, and tracked-client reads

## 16. Final judgment
- This design is above junior level because it is built on the right cache concepts and shows real awareness of concurrency, stale refill, and hot-key behavior.
- It is not yet interview-strong or production-strong as a framework because the abstraction boundaries are still too weak to reliably preserve those concepts under assembly and extension.
- Practical position:
  - good architectural instincts
  - meaningful groundwork for Strategy A / C / D
  - currently held back by type-safety gaps, invariant leaks, and missing executable validation
