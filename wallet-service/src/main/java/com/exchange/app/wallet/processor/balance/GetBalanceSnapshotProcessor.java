package com.exchange.app.wallet.processor.balance;

import com.exchange.app.wallet.dao.store.BalanceSnapshotStore;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class GetBalanceSnapshotProcessor {
    private final BalanceSnapshotStore balanceSnapshotStore;

    public enum LookupType {
        WALLET_ID, REF_ID
    }

    public Result<BalanceSnapshot> getBalanceSnapshot(LookupType type, String lookupValue) {
        if (Strings.isEmpty(lookupValue)) {
            log.error("lookup_value is required");
            return Result.failure(WalletServiceErrorCode.INVALID_REQUEST_PARAMETER, "lookup_value is required");
        }
        if (type == null) {
            log.error("lookup_type is required");
            return Result.failure(WalletServiceErrorCode.INVALID_REQUEST_PARAMETER, "lookup_type is required");
        }

        Result<BalanceSnapshot> snapshotResult;
        switch (type) {
            case WALLET_ID:
                snapshotResult = balanceSnapshotStore.getByWalletId(lookupValue);
                break;
            case REF_ID:
                snapshotResult = balanceSnapshotStore.getByRefId(lookupValue);
                break;
            default:
                log.error("invalid lookup type: {}", type);
                return Result.failure(WalletServiceErrorCode.INVALID_ENUM_ERROR, "invalid lookup type: " + type);
        }

        if (!snapshotResult.isSuccess()) {
            log.error("load balance snapshot failed, {}: {}", type.name(), lookupValue);
            return Result.failure(WalletServiceErrorCode.SERVER_ERROR, "load balance snapshot failed");
        }
        if (snapshotResult.getValue() == null) {
            log.error("balance snapshot not found for {}: {}", type, lookupValue);
            return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "balance snapshot not found");
        }
        return snapshotResult;
    }
}
