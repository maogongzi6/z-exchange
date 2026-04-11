# Cache Error Handling Follow-up Evaluation

## Verdict

Your direction is partly right, but proposal `2` is currently the critical problem.

Moving `@CacheExceptionToResult` from the concrete strategy bean to `StrategyFactory` does not give you a customizable downgrade boundary in the current Spring setup. It moves the annotation onto the factory method invocation, not onto the returned strategy bean's later `get()/afterDbHit()/afterDbMiss()` calls.

So the design goal is good:

- keep core strategy logic free of downgrade policy
- let services choose soft-fail vs throw

But the current mechanism does not actually deliver that goal.

## Current Code Findings

### 1. `FIELD` target adds no behavior today

`CacheExceptionToResult` now includes `ElementType.FIELD`:

- `common-util/src/main/java/com/exchange/common/redis/aop/CacheExceptionToResult.java:7`

But the AOP pointcut still only matches type-level or method-level annotations:

- `common-util/src/main/java/com/exchange/common/redis/aop/CacheExceptionToResultAop.java:17`

`@Around("@within(CacheExceptionToResult) || @annotation(CacheExceptionToResult)")`

That means:

- annotating a field has no effect
- Spring AOP will not intercept field access here
- this is dead API surface unless you add custom bean-wrapping infrastructure

### 2. Moving the annotation to `StrategyFactory` is not functionally correct

Current factory methods:

- `common-util/src/main/java/com/exchange/common/redis/cache/strategy/impl/StrategyFactory.java:26`
- `common-util/src/main/java/com/exchange/common/redis/cache/strategy/impl/StrategyFactory.java:43`

These annotated methods only wrap calls to:

- `buildRawCacheSuppressExceptionStrategy(...)`
- `buildVersionCacheSuppressExceptionStrategy(...)`

That advice can only intercept exceptions thrown while building the strategy object itself.

It cannot intercept later calls on the returned `RawCacheStrategy` / `VersionCacheStrategy` bean, because those calls happen on the returned object, not on `StrategyFactory`.

This is the main hidden flaw in the proposed design.

### 3. Wallet and ledger are still wired to the non-suppressing builders

Current registrations still use the plain builders:

- `ledger-service/src/main/java/com/exchange/app/ledger/dao/cache/LedgerCacheRegister.java:47`
- `ledger-service/src/main/java/com/exchange/app/ledger/dao/cache/LedgerCacheRegister.java:68`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:48`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:69`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/cache/BalanceSnapshotCacheRegister.java:92`

So even before judging the design, the current code does not follow the intended soft-fail boundary consistently.

### 4. Store code still assumes cache strategies return `Result`, not exceptions

Examples:

- `ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:49`
- `ledger-service/src/main/java/com/exchange/app/ledger/dao/store/LedgerTxnStore.java:68`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/store/BalanceSnapshotStore.java:69`
- `wallet-service/src/main/java/com/exchange/app/wallet/dao/store/BalanceSnapshotStore.java:81`

These stores log unsuccessful `Result` and continue main flow. They do not catch `CacheException`.

So if the strategy bean is not actually wrapped by a downgrade mechanism, cache exceptions can now escape and block main flow. In this repo, that is a real behavioral regression, not just a style issue.

## Proposal-by-Proposal Evaluation

## 1. Add `ElementType.FIELD` to `CacheExceptionToResult`

### Evaluation

Not useful with the current AOP design.

Adding `FIELD` only makes sense if you also add infrastructure that inspects annotated injection points or bean fields and wraps the injected bean accordingly. Plain Spring AOP will not do that for this annotation.

### Recommendation

Remove `FIELD` unless you are explicitly implementing one of these:

- a custom `BeanPostProcessor` that checks field/injection-point annotations
- a custom factory/wrapper mechanism that reads the annotation and returns a wrapped bean

Otherwise it invites a false assumption that field annotation changes runtime behavior.

## 2. Move `CacheExceptionToResult` from strategy to `StrategyFactory`

### Evaluation

The intention is good. The current implementation is not.

You want policy selection at construction/wiring time rather than baking it into the strategy class. That is a good goal for evolvability.

But annotating `StrategyFactory` methods is the wrong technical lever in this project, because the product object is what needs wrapping, not the factory call.

### Hidden risk

This design looks correct in code review because the registration site calls a "suppress exception" builder, but the runtime behavior is still wrong. That is exactly the kind of proxy-driven coupling that becomes hard to debug later.

### Better alternative

Use explicit wrappers from `StrategyFactory`, not AOP on factory methods.

Recommended shape:

- `buildRawCacheStrategy(...)` returns the plain strategy
- `buildRawCacheSuppressExceptionStrategy(...)` returns a decorator that implements `RawCacheStrategy`
- `buildVersionCacheSuppressExceptionStrategy(...)` returns a decorator that implements `VersionCacheStrategy`

That gives you actual customization at the bean wiring point, without hiding the downgrade behind Spring proxy rules.

This is better than both:

- annotating `StrategyFactory`
- annotating fields

If you still want AOP, the annotation must live on the actual strategy bean class or on a managed wrapper bean, not on the factory method.

## 3. Replace codec `UNEXPECTED_INTERNAL` with `IllegalArgumentException` / `IllegalStateException`

### Evaluation

Mostly correct, but only for encode-side contract violations.

Current encode-side `UNEXPECTED_INTERNAL` cases:

- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:29`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:39`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/ValueCodec.java:52`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/VersionCodec.java:27`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/codec/impl/VersionCodec.java:32`

These are not all the same kind of problem:

- invalid caller input or contract misuse -> `IllegalArgumentException`
- impossible branch / unsupported internal state -> `IllegalStateException`

That is cleaner than forcing everything into `CacheException(UNEXPECTED_INTERNAL)`.

### Important boundary

Do not apply this to decode-time failures.

Decode-time failures are persisted cache data problems and should remain cache-domain failures:

- malformed wire format -> `CacheException(MALFORMED_VALUE)`
- decode/JSON failure -> `CacheException(SERIALIZATION)`

That is what supports self-recover in `get()`.

### Recommendation

Use this split:

- `cacheType == null`, invalid content for type, negative version -> `IllegalArgumentException`
- impossible internal branch after invariant checks -> `IllegalStateException`
- bad stored cache payload -> keep `CacheException`

## 4. Catch `IllegalArgumentException` / `IllegalStateException` in `CacheExceptionToResult` and convert to `UNEXPECTED_INTERNAL`

### Evaluation

I do not recommend this as a broad boundary rule.

Why it is risky in this repo:

- wallet and ledger stores already treat failed cache `Result` as soft failure and continue
- broad conversion of `IllegalArgumentException` / `IllegalStateException` will hide programmer and wiring bugs behind cache-soft-fail behavior

This is especially dangerous because the strategy methods do more than codec calls. A generic `IllegalArgumentException` from future config validation, key construction, or feature wiring would also be silently downgraded.

This is the same smell the earlier evaluation warned about, just with narrower exception types.

### Concrete example from current code

Writer misuse already throws `IllegalArgumentException`:

- `common-util/src/main/java/com/exchange/common/redis/cache/component/support/RawCacheWriter.java:19`
- `common-util/src/main/java/com/exchange/common/redis/cache/component/support/VersionCacheWriter.java:24`

If `CacheExceptionToResult` starts swallowing generic `IllegalArgumentException`, those programmer-misuse cases become silent cache failures too.

### Better alternatives

Preferred:

- keep `CacheExceptionToResult` catching `CacheException` only
- translate known cache-owned misuse closer to the source

Minimal design that stays precise:

- `ValueCodec` / `VersionCodec` throw `IllegalArgumentException` / `IllegalStateException` on encode misuse
- `RawCacheWriter` / `VersionCacheWriter` catch those codec exceptions and rethrow `CacheException(UNEXPECTED_INTERNAL, cause)`
- `CacheExceptionToResult` still catches only `CacheException`

That keeps the downgrade boundary narrow and avoids turning arbitrary JDK runtime exceptions into soft cache failures.

If you want an even cleaner model, introduce a cache-owned runtime type such as `CacheContractException` and catch only that, not generic JDK exceptions.

## 5. Switch wallet/ledger to `CacheExceptionToResult`-based bean

### Evaluation

Yes in principle, but only after the suppressing bean mechanism is corrected.

The service behavior clearly wants soft-fail cache beans:

- `LedgerTxnStore` logs cache failure and continues DB/main flow
- `BalanceSnapshotStore` does the same

So using a non-suppressing strategy bean there is the wrong fit.

### Current problem

Even if you switch the registers to:

- `buildRawCacheSuppressExceptionStrategy(...)`
- `buildVersionCacheSuppressExceptionStrategy(...)`

that still does not work correctly if those methods only rely on AOP at the factory method level.

### Recommendation

Switch ledger/wallet to suppressing beans only after you implement one of these:

1. explicit suppressing wrapper strategies from `StrategyFactory`
2. strategy bean classes or managed wrappers that are actually proxied

For this project, option `1` is the better choice. It is clearer, smaller, and easier to verify.

## Recommended Final Shape

I would adjust your design like this:

1. Keep decode failures as `CacheException`

- `MALFORMED_VALUE`
- `SERIALIZATION`

2. Change encode-side misuse in `ValueCodec` / `VersionCodec`

- use `IllegalArgumentException` for bad inputs
- use `IllegalStateException` for impossible branches

3. Do not broaden `CacheExceptionToResult`

- keep it handling `CacheException` only

4. Translate encode-side misuse at cache support boundaries

- in `RawCacheWriter`
- in `VersionCacheWriter`
- optionally in any other component that directly uses codec encode paths

5. Make strategy suppression explicit via wrappers

- plain strategy bean for callers that want exceptions
- suppressing strategy bean for wallet/ledger-style soft-fail usage

6. Make service registration explicit

- ledger/wallet registers should intentionally choose suppressing variants
- future services can choose plain variants if they want fail-fast behavior

## Bottom Line

Your goal is correct: separate cache core logic from error-downgrade policy and allow services to choose soft-fail behavior.

The weak points are:

- `FIELD` target currently does nothing
- factory-method AOP does not wrap returned strategy calls
- broad `IllegalArgumentException` / `IllegalStateException` downgrade would hide real bugs

So my recommendation is:

- keep the semantic cleanup in `ValueCodec` / `VersionCodec`
- keep `CacheExceptionToResult` narrow
- move customization into explicit strategy wrappers returned by `StrategyFactory`
- then switch wallet/ledger to those suppressing beans

That preserves your design direction, fixes the current proxy gap, and keeps the layering predictable for future subsystems.
