package com.exchange.app.wallet.processor.transaction.step;

import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UpdateBalanceSnapshotProcessor {
    private final BalanceSnapshotRepository balanceSnapshotManager;

    public Result<List<BalanceSnapshot>> updateSnapshots(List<BalanceSnapshot> snapshots,
                                                          Function<BalanceSnapshot, Result<BalanceSnapshot>> function) {
        if (snapshots.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<Long> modifySnapshotIds = snapshots.stream()
                .sorted(Comparator.comparing(BalanceSnapshot::getId))
                .map(BalanceSnapshot::getId)
                .distinct()
                .collect(Collectors.toList());
        // Lock by PK order before applying balance mutations so multi-wallet transactions avoid lock-order drift.
        var snapshotFromDb = balanceSnapshotManager.selectInIdForUpdate(modifySnapshotIds);
        if (snapshotFromDb.size() != modifySnapshotIds.size()) {
            return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND,
                    String.format("balance_snapshot_not_found, ids: %s, snapshotFromDb: %s", modifySnapshotIds, snapshotFromDb));
        }

        List<BalanceSnapshot> updatedSnapshots = new ArrayList<>();
        for (BalanceSnapshot snapshot : snapshotFromDb) {
            BalanceSnapshot copy = new BalanceSnapshot();
            BeanUtils.copyProperties(snapshot, copy);

            Result<BalanceSnapshot> result = function.apply(snapshot);
            if (result.isFailed()) {
                return Result.failure(result);
            }
            snapshot = result.getValue();

            if (!Objects.equals(snapshot, copy)) {
                if (balanceSnapshotManager.updateBalanceWithInOptimisticLock(snapshot, copy) != 1) {
                    return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED,
                            String.format("failed to update balance snapshot, snapshot: %s, copy: %s", snapshot, copy));
                }
                updatedSnapshots.add(snapshot);
            }
        }
        return Result.success(updatedSnapshots);
    }
}
