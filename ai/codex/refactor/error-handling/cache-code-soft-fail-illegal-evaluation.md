# Cache Soft-Fail Illegal Exception Evaluation

## Verdict

Your goal is reasonable:

- ledger and wallet treat cache as a soft dependency
- cache-side programmer-misuse-like failures in the cache implementation should not block their main flow
- suppressing vs throwing should be chosen at strategy construction time

But the proposed design is only partly good as written.

The strongest parts are:

- switching wallet/ledger to explicit suppress-exception strategy beans
- cleaning encode-side codec misuse out of `CacheException`

The weakest part is:

- using a second broad AOP that downgrades generic `IllegalArgumentException` / `IllegalStateException`

In this codebase, that is acceptable only if it is strictly limited to the suppress-exception strategy variant used by ledger/wallet. It is not a good general cache boundary rule.

## Current Code Findings

### 1. Wallet and ledger still use the non-suppressing builders

Current registrations still call the plain builders:

- `ledger-service/src/main/java/com/exchange/app/ledger/dao/cache/LedgerCacheRegister.java:47`
- `ledger-service/src/main/java/com/exchange/app/ledger/dao/cache/LedgerCacheRegister.java:68`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:48`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:69`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:92`

That is inconsistent with the stated requirement, because the store layer clearly expects cache calls to degrade into unsuccessful `Result`, not escape as runtime exceptions.

### 2. The suppress-exception proxy path is still not complete

`StrategyFactory` now creates manual proxies:

- `common-util/src/main/java/com/exchange/common/redis/cache/strategy/impl/StrategyFactory.java:28-35`
- `common-util/src/main/java/com/exchange/common/redis/cache/strategy/impl/StrategyFactory.java:48-55`

This is directionally better than annotating the factory method itself, but the current advice being added is still the annotation-based aspect:

- `common-util/src/main/java/com/exchange/common/redis/aop/CacheExceptionToResultAop.java:15-23`
- `common-util/src/main/java/com/exchange/common/redis/aop/CacheExceptionToResult.java:5-8`

The aspect pointcut matches only:

- `@within(CacheExceptionToResult)`
- `@annotation(CacheExceptionToResult)`

The returned `DefaultRawCacheStrategy` and `DefaultVersionCacheStrategy` are not annotated, so the proxy-specific aspect still has no matching join point unless you also annotate the target or change the advice mechanism.

So the design direction is right, but the current implementation model is still proxy-fragile.

### 3. Store code definitely wants soft-fail semantics

Both stores explicitly log cache failure and continue:

- `ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:49-76`
- `ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:100-127`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/store/BalanceSnapshotStore.java:69-88`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/store/BalanceSnapshotStore.java:118-141`

So a suppressing strategy variant is not optional for these services. It is the correct runtime contract.

### 4. The current `IllegalArgumentException` sites are narrow and cache-owned

Today the obvious cache-owned ones are:

- `common-util/src/main/java/com/exchange/common/redis/cache/component/support/RawCacheWriter.java:19`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/support/VersionCacheWriter.java:24`

And the encode-side `UNEXPECTED_INTERNAL` sites that you want to refactor are:

- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:29`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:39`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:52`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/VersionCodec.java:27`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/VersionCodec.java:32`

Those are good candidates for cleanup. But a broad catch at the strategy boundary would also catch future `IllegalArgumentException` / `IllegalStateException` from places that are not codec contract issues.

## Evaluation Of Your Design

## 1. New AOP to downgrade `IllegalArgumentException` / `IllegalStateException` to `UNEXPECTED_INTERNAL`

### What it solves

It solves a real inconsistency:

- some encode-side misuse is still represented as `CacheException(UNEXPECTED_INTERNAL)`
- some misuse already throws `IllegalArgumentException`
- ledger/wallet want both to be soft-fail cache errors

So there is a real gap to close.

### Main weakness

`IllegalArgumentException` and `IllegalStateException` are too generic to be a safe cache boundary on their own.

In this project, if you downgrade them broadly at strategy level, you are saying:

- any illegal argument in strategy execution is now just a cache failure
- any illegal state in strategy execution is now just a cache failure

That is wider than the requirement actually says.

The requirement is:

- cache-owned encode/write misuse should not block ledger/wallet main flow

It is not:

- all illegal argument/state bugs anywhere under strategy execution should silently become cache failure

### Logging risk

You specifically propose an error log in the new AOP. Current source sites already log before throwing:

- `ValueCodec`
- `VersionCodec`
- `RawCacheWriter`
- `VersionCacheWriter`

So adding an AOP error log will likely double-log the same failure unless you deliberately change source logging policy.

### Recommendation

If you keep this design, scope it tightly:

- only on suppress-exception strategy proxies
- only for ledger/wallet-style beans
- log with full stack trace once at the downgrade boundary
- keep the log message explicit that this is a softened cache-internal contract failure

But I would still prefer a more precise exception type than raw JDK exceptions.

## 2. Build suppress-exception strategies with `AspectJProxyFactory`, add both AOPs

### Direction

The construction-time policy split is good:

- `buildRawCacheStrategy(...)` for fail-fast callers
- `buildRawCacheSuppressExceptionStrategy(...)` for soft-fail callers

That matches the needs of this repo.

### Technical weakness

Using `AspectJProxyFactory` is fine in principle, but your current aspect design still depends on annotation pointcuts. That is the wrong fit for manually created strategy proxies.

If the suppressing proxy is explicit, the advice should also be explicit.

### Better alternatives

Best option for this repo:

- use a plain decorator implementation of `RawCacheStrategy`
- use a plain decorator implementation of `VersionCacheStrategy`

Each decorator can catch:

- `CacheException`
- selected cache-owned contract exceptions

and return `Result.failure(...)`.

That is much easier to reason about than stacking two aspects plus ordering plus pointcut matching.

If you insist on `AspectJProxyFactory`, use `addAdvice(...)` with a direct `MethodInterceptor`, not annotation-based aspects. Then the proxy behavior is explicit and does not depend on target annotations.

## 3. Refactor codec `UNEXPECTED_INTERNAL` to `IllegalArgumentException` / `IllegalStateException`

### Evaluation

This part is good, with one boundary:

- do it only for encode-side contract violations
- do not do it for decode-side cache data failures

Good candidates:

- invalid encode content/type combination -> `IllegalArgumentException`
- negative version -> `IllegalArgumentException`
- impossible internal branch after validation -> `IllegalStateException`

Keep as `CacheException`:

- malformed persisted cache value
- serialization/deserialization failure on decode

That preserves self-recover behavior in `get()`.

### Project-specific note

This cleanup is more coherent if you also make writer behavior consistent. Right now the writers already use `IllegalArgumentException`, while codecs still partly use `CacheException(UNEXPECTED_INTERNAL)`. Your proposal fixes that inconsistency.

## 4. Switch wallet/ledger to suppress-exception strategy beans

### Evaluation

Yes. This is required if you want the runtime behavior to match the store code.

Right now the store code assumes cache calls return unsuccessful `Result` on failure:

- `LedgerTxnStore`
- `BalanceSnapshotStore`

So using the suppress-exception builders there is correct.

### Hidden risk

Do not switch those services until the suppressing builder is actually reliable. Otherwise you will think you are protecting main flow while exceptions still escape.

## Better Alternative

I would adjust your design in one important way:

Do not introduce a generic second AOP for `IllegalArgumentException` / `IllegalStateException`.

Instead:

1. Refactor codec encode misuse to `IllegalArgumentException` / `IllegalStateException`.
2. Catch those exceptions in cache support/writer boundaries and wrap them into `CacheException(UNEXPECTED_INTERNAL, cause)`.
3. Keep the suppressing strategy boundary catching `CacheException` only.
4. Use explicit suppressing strategy decorators or direct proxy interceptors in `StrategyFactory`.
5. Wire wallet/ledger to the suppressing variants.

Why this is better in this repo:

- the downgrade boundary stays cache-specific
- you do not silently swallow generic JDK runtime exceptions from unrelated future code
- stack traces are preserved via `cause`
- policy remains explicit: only cache-owned failures are softened

This also avoids the double-logging problem, because source components can either:

- throw wrapped `CacheException` without logging, and let the suppressing boundary log
- or log once at the source and keep the boundary quiet

## If You Still Want The New AOP

If you want to keep your proposed design with a new AOP, I would set these rules:

1. The new advice must be used only on suppress-exception strategy proxies, never globally.
2. It should convert only `IllegalArgumentException` / `IllegalStateException` from the cache strategy call path to `Result.failure(CacheErrorCode.UNEXPECTED_INTERNAL, ...)`.
3. The log message should clearly say this is a softened cache implementation misuse/illegal state, not an expected operational failure.
4. Do not rely on annotation pointcuts for those manual proxies. Use direct advice/interceptor attachment.
5. Remove or reduce duplicate source logging for the same failure path.

Even with those guards, I still consider the wrapper-to-`CacheException` approach cleaner.

## Bottom Line

Your design is directionally correct in two places:

- make wallet/ledger explicitly use soft-fail strategy beans
- move encode-side misuse in codecs from `CacheException(UNEXPECTED_INTERNAL)` to `IllegalArgumentException` / `IllegalStateException`

The weak point is the new generic illegal-argument/state downgrade AOP.

For this repo, the better design is:

- normalize cache-owned misuse into `CacheException(UNEXPECTED_INTERNAL)` near cache support boundaries
- keep the strategy soft-fail boundary catching `CacheException` only
- implement the soft-fail strategy variant explicitly in `StrategyFactory`

That matches the ledger/wallet requirement without turning broad JDK runtime exceptions into silent cache misses.
