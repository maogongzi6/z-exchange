package com.exchange.common.redis.idemp;

import com.exchange.common.redis.idemp.utils.IdempValue;
import com.exchange.common.result.Result;

import java.time.Duration;

public interface IdempotencyClient {
    Result<Boolean> claimIdempIfAbsent(String service, String scope, String idempId,
                                       String hash, String token, Duration ttl);

    Result<Void> markIdempDone(String service, String scope, String idempId,
                               String hash, String token, String content, Duration ttl);

    Result<IdempValue> getIdemp(String service, String scope, String idempId);

    Result<IdempValue> getIdempAndVerifyHash(String service, String scope,
                                             String idempId, String expectedHash);

    Result<String> releaseIdempIfOwned(String service, String scope,
                                       String idempId, String expectedValue);

    Result<Boolean> forceDeleteIdemp(String service, String scope, String idempId);
}
