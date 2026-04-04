package com.exchange.app.wallet.processor.transaction.step;

import com.exchange.app.wallet.config.CustomCacheProperties;
import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.processor.transaction.model.RequestInfo;
import com.exchange.app.wallet.processor.transaction.util.Constant;
import com.exchange.app.wallet.processor.transaction.util.ValidateHelper;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.redis.idemp.utils.IdempValue;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.utils.JitterHelper;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.common.ServiceIdPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@EnableConfigurationProperties(CustomCacheProperties.Idemp.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class IdempPrecheckProcessor {
    private final IdempRedisClient idempRedisClient;

    private final CustomCacheProperties.Idemp idempConfig;

    private final WalletTransactionRepository walletTransactionManager;

    // return txn_id if exists
    // return null if not exists
    public Result<String> idempAndValidatePrecheck(RequestInfo requestInfo) {
        Result<Void> precheckResult = ValidateHelper.requestPrecheck(requestInfo);
        if (precheckResult.isFailed()) {
            // fast fail before claim idemp
            return Results.fail(precheckResult);
        }

        Result<IdempValue> valueResult = claimIdempCacheIfAbsent(requestInfo);
        // there should be no idemp value with the given token in the cache if fails here. no need to release here
        if (valueResult.isFailed()) {
            return Results.fail(valueResult);
        }

        IdempValue value = valueResult.value();
        // when value is not null, it means claim idemp fails because it has been claimed, no need to release here
        if (value != null) {
            switch (value.status) {
                case PENDING:
                    // another request with same params is in processing (but likely no wallet_txn has been persisted in db)
                    log.error("another same request in processing, requestInfo:{}, idempValue:{}", requestInfo, value);
                    return Results.fail(ErrorCode.REQUEST_IN_PROCESSING, "request still processing");
                case ACCEPTED:
                    // return txn id
                    return Results.success(value.content, "wallet txn already accepted");
                default:
                    log.error("unknown idemp state:{}", value);
                    return Results.fail(ErrorCode.INVALID_ENUM_ERROR, "unknown idemp state");
            }
        }

        WalletTransaction walletTxn = idempDbCheck(requestInfo.serviceIdPb, requestInfo.idempotenceKey);
        if (walletTxn != null) {
            // set idemp to DONE when found wallet txn
            idempRedisClient.markIdempDone(GlobalServiceId.WALLET.code, Constant.SCOPE, requestInfo.idempotenceKey,
                    String.valueOf(requestInfo.getStableHash()), requestInfo.token, walletTxn.getTxnId(),
                    JitterHelper.jitter(idempConfig.getDoneTtl(), idempConfig.getJitterMs()));

            log.info("wallet txn already exists: {}", walletTxn);
            return Results.success(walletTxn.getTxnId(), "wallet txn already exists");
        }

        return Results.success();
    }



    // return null if set success
    // return idemp info in the cache when fail to set
    private Result<IdempValue> claimIdempCacheIfAbsent(RequestInfo requestInfo) {
        String globalCode = GlobalServiceId.WALLET.code;
        String reqHash = requestInfo.getStableHash();
        String key = CommonIdempHelper.idempKey(globalCode, Constant.SCOPE, requestInfo.idempotenceKey);
        Boolean success = idempRedisClient.claimIdempIfAbsent(globalCode, Constant.SCOPE, requestInfo.idempotenceKey,
                reqHash, requestInfo.token, JitterHelper.jitter(idempConfig.getPendingTtl(), idempConfig.getJitterMs()));
        if (success) {
            return Results.success();
        }

        String idempV = idempRedisClient.getIdemp(globalCode, Constant.SCOPE, requestInfo.idempotenceKey);
        Result<IdempValue> parseResult = CommonIdempHelper.parseIdempValue(idempV);
        if (parseResult.isFailed()) {
            log.error("parse idemp value failed, key:{} , value:{} result:{}", key, idempV, parseResult);
            // rare case, delete invalid value to self-recover.
            // force delete here, value is malformed and possibly cannot get a token to verify the owner.
            idempRedisClient.forceDeleteIdemp(globalCode, Constant.SCOPE, requestInfo.idempotenceKey);
            // return null then access db to check if exists
            return Results.success();
        }

        IdempValue idempValue = parseResult.value();
        if (!Objects.equals(idempValue.hash, reqHash)) {
            log.error("idemp value hashcode conflict, key:{}, value:{}, hash:{}, reqInfo: {}", key, idempValue, reqHash, requestInfo);
            return Results.fail(ErrorCode.REQUEST_HASH_CONFLICT, "idemp value hashcode conflict");
        }

        return Results.success(idempValue);
    }

    private WalletTransaction idempDbCheck(ServiceIdPb serviceIdPb, String idempotencyKey) {
        // TODO add hash check here
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(serviceIdPb);
        return walletTransactionManager.selectByIdempotencyKey(serviceId, idempotencyKey);
    }
}
