package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.config.CustomCacheConfig;
import com.exchange.app.ledger.dao.repository.AccountRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.dao.store.LedgerTxnStore;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.redis.idemp.utils.IdempValue;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.utils.JitterHelper;
import com.exchange.common.utils.StableHashHelper;
import com.exchange.common.utils.TokenHelper;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.app.ledger.utils.EnumMappers;
import com.exchange.app.ledger.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PostLedgerProcessor {
    static final private String SCOPE = "post_ledger";

    final private CustomCacheConfig.Idemp idempConfig;

    final private LedgerEntryMapper ledgerEntryMapper;
    final private AccountRepository accountManager;
    final private LedgerTxnRepository ledgerTxnRepository;

    final private IdempRedisClient idempRedisClient;

    final private DbTxnExecutor dbTxnExecutor;
    private final LedgerTxnStore ledgerTxnStore;

    public PostTransactionReplyPb postTransaction(PostTransactionRequestPb req) {
        String token = TokenHelper.generateToken(GlobalServiceId.LEDGER.name());

        Result<String> txnIdResult = idempClaimAndReqValidate(req, token);
        if (txnIdResult.isFailed()) {
            // TODO no need to release claimed key here, since there should not be a key in cache with given token
            return onError(req, token, Results.getErrorCode(txnIdResult), txnIdResult.errorDetail);
        } else if (!Strings.isEmpty(txnIdResult.value)) {
            return onSuccess(req.getReferenceId(), txnIdResult.value, "already exists");
        }

        String txnId = IdGenerator.generateLedgerTxnId();
        LedgerTxn ledgerTxn = LedgerTxn.create(txnId, req.getReferenceId());
        List<String> accountRefs = req.getEntriesList().stream().map(LedgerEntryPb::getAccountRef).distinct().collect(Collectors.toList());
        Map<String, String> accountRefToAccountId = accountManager.getAccountIdInRef(accountRefs).stream()
                .collect(Collectors.toMap(Account::getReferenceId, Account::getAccountId));
        if (accountRefToAccountId.size() != accountRefs.size()) {
            Set<String> notFound = new HashSet<>(accountRefs) {{removeAll(accountRefToAccountId.keySet());}};
            log.error("account not found, notFoundRefs={}", notFound);
            return onError(req, token, ErrorCode.ACCOUNT_NOT_FOUND, "account_not_found, refs: " + notFound);
        }

        List<LedgerEntry> entries = new ArrayList<>();
        for (LedgerEntryPb entryPb : req.getEntriesList()) {
            entries.add(createLedgerEntry(txnId, entryPb, accountRefToAccountId.get(entryPb.getAccountRef())));
        }
        Result<Void> result = dbTxnExecutor.executeWithDefault(() -> {
            if (ledgerTxnRepository.insertIgnore(ledgerTxn) == 0) {
                // TODO maybe get txn and return if info match
                log.error("duplicated ledger txn: {}", ledgerTxn);
                return Results.fail(ErrorCode.LEDGER_DUPLICATED, "duplicated ledger txn");
            }
            if (ledgerEntryMapper.batchInsert(entries) < entries.size()) {
                log.error("unexpected ledger txn already exists, {}", ledgerTxn);
                // should be a server error?
                return Results.fail(ErrorCode.SERVER_ERROR, "unexpected duplicated_ledger_entry");
            }
            return Results.success();
        });
        if (result.isFailed()) {
            return onError(req, token, Results.getErrorCode(result), result.errorDetail);
        }
        // clean negative cache (if exist) after inserting ledger txn
        ledgerTxnStore.cleanNegativeCacheAfterInsert(ledgerTxn.getReferenceId());
        // set idemp to DONE when found ledger txn
        idempRedisClient.markIdempDone(GlobalServiceId.LEDGER.code, SCOPE, req.getReferenceId(), getReqStableHash(req),
                token, txnId, JitterHelper.jitter(idempConfig.getDoneTtl(), idempConfig.getJitterMs()));

        return onSuccess(req.getReferenceId(), txnId, "success");
    }

    private Result<String> idempClaimAndReqValidate(PostTransactionRequestPb req, String token) {
        Result<Void> precheckResult = reqPrecheck(req);
        if (precheckResult.isFailed()) {
            return Results.fail(precheckResult);
        }

        Result<IdempValue> idempValueResult = claimIdempCacheIfAbsent(req, token);
        if (idempValueResult.isFailed()) {
            return Results.fail(idempValueResult);
        }

        IdempValue idempValue = idempValueResult.value;
        if (idempValue != null) {
            switch (idempValue.status) {
                case PENDING:
                    // another request with same params is in processing (but likely no wallet_txn has been persisted in db)
                    log.error("another same request in processing, request:{}, idempValue:{}", req, idempValue);
                    return Results.fail(ErrorCode.REQUEST_IN_PROCESSING, "request still processing");
                case ACCEPTED:
                    log.info("request accepted, idempValue:{}", idempValue);
                    return Results.success(idempValue.content);
                default:
                    log.error("unknown idemp state:{}", idempValue);
                    return Results.fail(ErrorCode.INVALID_ENUM_ERROR, "unknown idemp state");
            }
        }

        LedgerTxn txn = ledgerTxnRepository.getByRefId(req.getReferenceId());
        if (txn != null) {
            // set idemp to DONE when found ledger txn
            idempRedisClient.markIdempDone(GlobalServiceId.LEDGER.code, SCOPE, req.getReferenceId(), getReqStableHash(req),
                    token, txn.getTxnId(), JitterHelper.jitter(idempConfig.getDoneTtl(), idempConfig.getJitterMs()));
            log.info("ledger txn already exists, {}", txn);
            return Results.success(txn.getTxnId());
        }
        return Results.success();
    }

    private Result<IdempValue> claimIdempCacheIfAbsent(PostTransactionRequestPb req, String token) {
        String reqHash = getReqStableHash(req);
        if (idempRedisClient.claimIdempIfAbsent(GlobalServiceId.LEDGER.code, SCOPE, req.getReferenceId(),
                reqHash, token, JitterHelper.jitter(idempConfig.getPendingTtl(), idempConfig.getJitterMs()))) {
            return Results.success();
        }

        String idempV = idempRedisClient.getIdemp(GlobalServiceId.LEDGER.code, SCOPE, req.getReferenceId());
        Result<IdempValue> valueResult = CommonIdempHelper.parseIdempValue(idempV);
        if (valueResult.isFailed()) {
            log.error("parse idemp value failed, key:{} , value:{}, result:{}", req.getReferenceId(), idempV, valueResult);
            // rare case, delete invalid value to self-recover.
            // force delete key here, value is malformed and possibly cannot get a token to verify the owner.
            // this could mis-delete the key created by a concurrent identical request, but this is extremely rare,
            // and we have db idemp as the backstop
            idempRedisClient.forceDeleteIdemp(GlobalServiceId.LEDGER.code, SCOPE, req.getReferenceId());
            // return null, then access db to check if exists
            return Results.success();
        }
        IdempValue idempValue = valueResult.value;
        if (!Objects.equals(idempValue.hash, reqHash)) {
            log.error("req hash does not match, reqHash={}, idemp: {}", reqHash, idempValue);
            return Results.fail(ErrorCode.REQUEST_HASH_CONFLICT, "req_hash_conflict");
        }
        return Results.success(idempValue);
    }

    private Result<Void> reqPrecheck(PostTransactionRequestPb req) {
        Map<String, Long> debitSumMap = new HashMap<>(), creditSumMap = new HashMap<>();
        int debitCount = 0, creditCount = 0;

        for (LedgerEntryPb entry : req.getEntriesList()) {
            if (entry.getAmount() == 0) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "zero_amount: " + entry);
            }
            Direction d = EnumMappers.directionPbMapper.to(entry.getDirection());
            String assetId = entry.getAssetId();
            if (d == null || d == Direction.UNKNOWN) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_direction: " + Direction.UNKNOWN);
            }
            if (d == Direction.DEBIT) {
                debitCount++;
            } else {
                creditCount++;
            }
            Map<String, Long> targetSumMap = d == Direction.DEBIT ? debitSumMap : creditSumMap;
            if (!targetSumMap.containsKey(assetId)) {
                targetSumMap.put(assetId, 0L);
            }
            targetSumMap.put(assetId, targetSumMap.get(assetId) + entry.getAmount());
        }

        if (debitCount == 0 || creditCount == 0) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }

        if (debitSumMap.size() != creditSumMap.size()) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }
        for (Map.Entry<String, Long> entry: debitSumMap.entrySet()) {
            String assetId = entry.getKey();
            Long debitAmount = entry.getValue();
            if (!Objects.equals(creditSumMap.get(assetId), debitAmount)) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER,("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap));
            }
        }
        return Results.success();
    }

    private LedgerEntry createLedgerEntry(String txnId, LedgerEntryPb entryPb, String accountId) {
        return LedgerEntry.create(
                IdGenerator.generateLedgerEntryId(),
                txnId,
                entryPb.getAssetId(),
                accountId,
                entryPb.getAmount(),
                EnumMappers.directionPbMapper.to(entryPb.getDirection())
        );
    }

    private PostTransactionReplyPb onSuccess(String walletReferenceId, String ledgerTxnId, String detail) {
        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        return builder.setReferenceId(walletReferenceId)
                .setLedgerTxnId(ledgerTxnId)
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail)).build();
    }

    // release the owned idemp key if txn not persisted in db when an error occurs. this can help idemp check recover from error
    private PostTransactionReplyPb onError(PostTransactionRequestPb req, String token, ErrorCode errorCode, String detail) {
        releaseIdempIfOwned(req.getReferenceId(), getReqStableHash(req), token);

        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private void releaseIdempIfOwned(String refId, String hash, String token) {
        String idempV = CommonIdempHelper.idempPendingValue(hash, token);
        Result<String> releaseResult = idempRedisClient.releaseIdempIfOwned(GlobalServiceId.LEDGER.code, SCOPE, refId, idempV);
        if (releaseResult.isFailed()) {
            // log the error
            log.error("release idemp failed, key:{} , result:{}", refId, releaseResult);
        }
    }

    private String getReqStableHash(PostTransactionRequestPb req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.getReferenceId()).append("|")
                .append(req.getDescription()).append("|");
        List<LedgerEntryPb> sorted = new ArrayList<>(req.getEntriesList());
        sorted.sort(Comparator.comparing(LedgerEntryPb::getAccountRef)
                .thenComparing(LedgerEntryPb::getDirection)
                .thenComparing(LedgerEntryPb::getAmount)
                .thenComparing(LedgerEntryPb::getAssetId));
        for (LedgerEntryPb entry : sorted) {
            sb.append(entry.getAccountRef()).append("|")
                    .append(entry.getDirection()).append("|")
                    .append(entry.getAmount()).append("|")
                    .append(entry.getAssetId()).append("|");
        }
        return StableHashHelper.stableHash(sb.toString());
    }
}
