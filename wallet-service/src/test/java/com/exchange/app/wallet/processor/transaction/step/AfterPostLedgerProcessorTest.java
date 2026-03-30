package com.exchange.app.wallet.processor.transaction.step;

import com.exchange.app.wallet.config.CustomCacheConfig;
import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.dao.repository.WalletAccountMappingRepository;
import com.exchange.app.wallet.dao.repository.WalletActionRepository;
import com.exchange.app.wallet.dao.repository.WalletReservationRepository;
import com.exchange.app.wallet.dao.repository.WalletTransactionRepository;
import com.exchange.app.wallet.kafka.producer.DefaultPublisher;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletBucket;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.enums.transaction.ReservationOutcome;
import com.exchange.app.wallet.po.enums.transaction.ReservationStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.common.utils.result.Result;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AfterPostLedgerProcessorTest {
    @Test
    public void afterPostLedgerShouldMovePendingSettleToConsumed() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = (TransactionCallback<Object>) invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        IdempRedisClient idempRedisClient = mock(IdempRedisClient.class);
        CustomCacheConfig.Idemp idempConfig = new CustomCacheConfig.Idemp();
        BalanceSnapshotRepository balanceSnapshotRepository = mock(BalanceSnapshotRepository.class);
        WalletReservationRepository walletReservationRepository = mock(WalletReservationRepository.class);
        WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
        WalletActionRepository walletActionRepository = mock(WalletActionRepository.class);
        WalletAccountMappingRepository walletAccountMappingRepository = mock(WalletAccountMappingRepository.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        DefaultPublisher defaultPublisher = mock(DefaultPublisher.class);
        UpdateReservationProcessor updateReservationProcessor = new UpdateReservationProcessor(walletReservationRepository);

        AfterPostLedgerProcessor processor = new AfterPostLedgerProcessor(
                transactionTemplate,
                idempRedisClient,
                idempConfig,
                balanceSnapshotRepository,
                walletReservationRepository,
                walletTransactionRepository,
                walletActionRepository,
                walletAccountMappingRepository,
                outboxRepository,
                defaultPublisher,
                updateReservationProcessor
        );

        WalletTransaction txn = WalletTransaction.create("txn-1", "ref-1", ServiceId.USER, "idem-1", TransactionStatus.PENDING, TransactionType.ATOMIC, BusinessType.TRANSFER);
        txn.setId(100L);
        when(walletTransactionRepository.selectByTxnId("txn-1")).thenReturn(txn);
        when(walletTransactionRepository.updateTransactionStatus(txn, 100L, TransactionStatus.COMPLETED)).thenAnswer(invocation -> {
            txn.setTxnStatus(TransactionStatus.COMPLETED);
            return 1;
        });
        when(walletTransactionRepository.selectById(100L)).thenReturn(txn);

        WalletAction consume = WalletAction.create("action-1", "txn-1", "wallet-out", "asset-1", WalletBucket.RESERVED, ActionType.CONSUME, 10L, "reservation-ref-1");
        WalletAction transferOut = WalletAction.create("action-2", "txn-1", "wallet-out", "asset-1", WalletBucket.RESERVED, ActionType.TRANSFER_OUT, 10L, null);
        WalletAction transferIn = WalletAction.create("action-3", "txn-1", "wallet-in", "asset-1", WalletBucket.AVAILABLE, ActionType.TRANSFER_IN, 10L, null);
        when(walletActionRepository.selectByTxnId(eq("txn-1"), any())).thenReturn(List.of(consume, transferOut, transferIn));

        BalanceSnapshot outSnapshot = BalanceSnapshot.create("wallet-out", ServiceId.USER, "wallet-out-ref", "asset-1", WalletStatus.OPEN, OwnerType.USER, "owner-out", 0L, 10L);
        outSnapshot.setId(200L);
        BalanceSnapshot inSnapshot = BalanceSnapshot.create("wallet-in", ServiceId.USER, "wallet-in-ref", "asset-1", WalletStatus.OPEN, OwnerType.USER, "owner-in", 0L, 0L);
        inSnapshot.setId(201L);
        when(balanceSnapshotRepository.selectByWalletIds(anyList())).thenReturn(List.of(outSnapshot, inSnapshot));
        when(balanceSnapshotRepository.transferOutFromWalletId(200L, "wallet-out", "asset-1", 10L)).thenReturn(1);
        when(balanceSnapshotRepository.transferInToWalletId(201L, "wallet-in", "asset-1", 10L)).thenReturn(1);

        WalletReservation loadedReservation = WalletReservation.create("reservation-1", ServiceId.USER, "reservation-ref-1", "wallet-out", "wallet-out-ref", "asset-1", 10L, 0L, 0L, 10L, 0L, ReservationStatus.ACTIVE, ReservationOutcome.NOT_DONE, "reserve-txn-1");
        loadedReservation.setId(300L);
        WalletReservation lockedReservation = WalletReservation.create("reservation-1", ServiceId.USER, "reservation-ref-1", "wallet-out", "wallet-out-ref", "asset-1", 10L, 0L, 0L, 10L, 0L, ReservationStatus.ACTIVE, ReservationOutcome.NOT_DONE, "reserve-txn-1");
        lockedReservation.setId(300L);
        when(walletReservationRepository.selectByRefs(anyList())).thenReturn(List.of(loadedReservation));
        when(walletReservationRepository.selectInIdForUpdate(anyList())).thenReturn(List.of(lockedReservation));
        when(walletReservationRepository.updateWithOptimisticLock(any(), any())).thenReturn(1);

        Result<WalletTransaction> result = processor.afterPostLedger("txn-1");

        Assert.assertTrue(result.success);
        Assert.assertEquals(TransactionStatus.COMPLETED, result.value.getTxnStatus());

        ArgumentCaptor<WalletReservation> reservationCaptor = ArgumentCaptor.forClass(WalletReservation.class);
        verify(walletReservationRepository).updateWithOptimisticLock(reservationCaptor.capture(), any());
        WalletReservation updatedReservation = reservationCaptor.getValue();
        Assert.assertEquals(Long.valueOf(0L), updatedReservation.getPendingSettle());
        Assert.assertEquals(Long.valueOf(10L), updatedReservation.getConsumed());
        Assert.assertEquals(ReservationStatus.FINISHED, updatedReservation.getReservationStatus());
        Assert.assertEquals(ReservationOutcome.CONSUMED, updatedReservation.getReservationOutcome());
    }
}