//package com.exchange.app.wallet.processor.pressure;
//
//import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
//import com.exchange.app.wallet.po.enums.OwnerType;
//import com.exchange.app.wallet.po.enums.ServiceId;
//import com.exchange.app.wallet.po.enums.WalletStatus;
//import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
//import org.springframework.transaction.TransactionManager;
//import org.springframework.transaction.support.TransactionTemplate;
//
//import java.time.Duration;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Slf4j
//@RunWith(SpringJUnit4ClassRunner.class)
//@SpringBootTest
//public class SingleRowDbTest {
//    @Autowired
//    BalanceSnapshotRepository balanceSnapshotRepository;
//    @Autowired
//    TransactionTemplate transactionTemplate;
//
//    @Test
//    public void pressureTestUsingDevEnvDb() throws InterruptedException {
//        int size = 5;  // Placeholder for #{size}
//        long initialAmount = 1_000_000;
//        int threadCount = 50;
//        Duration testDuration = Duration.ofSeconds(60);
//        AtomicInteger successfulTransactions = new AtomicInteger(0);
//        AtomicInteger retryCount = new AtomicInteger(0);
//
//        // Step 1: Create balance snapshots
//        for (int i = 0; i < size; i++) {
//            BalanceSnapshot snapshot = new BalanceSnapshot();
//            snapshot.setWalletId("wallet" + i);
//            snapshot.setServiceId(ServiceId.SYSTEM);
//            snapshot.setWalletReferenceId("wallet" + i);
//            snapshot.setAssetId("asset" + i);
//            snapshot.setAvailable(initialAmount);
//            snapshot.setOwnerType(OwnerType.SYSTEM);
//            snapshot.setOwnerId("owner" + i);
//            snapshot.setWalletStatus(WalletStatus.OPEN);
//
//            balanceSnapshotRepository.insertIgnore(snapshot);
//        }
//
//        // Step 2: Create thread pool
//        ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
//
//        // Step 3: Schedule tasks
//        Runnable task = () -> {
//            long endTime = System.currentTimeMillis() + testDuration.toMillis();
//            while (System.currentTimeMillis() < endTime) {
//                try {
//                    // Start a new transaction
//                    transactionTemplate.execute(status -> {
//                        for (int i = 0; i < size; i++) {
//                            try {
//                                // Perform update operation
//                                balanceSnapshotRepository.reserveFromWalletId("wallet" + i, "asset" + i, 10);
//                            } catch (Exception e) {
//                                retryCount.incrementAndGet();
//                                throw e;
//                            }
//                        }
//                        successfulTransactions.incrementAndGet();
//                        return null;
//                    });
//                } catch (Exception e) {
//                    log.error("Transaction failed : {}", e.getMessage());
//                }
//            }
//        };
//
//        for (int i = 0; i < threadCount; i++) {
//            threadPool.submit(task);
//        }
//
//        // Step 4: Give threads time to execute
//        threadPool.shutdown();
//        threadPool.awaitTermination(testDuration.toMillis() + 5000, TimeUnit.MILLISECONDS);
//
//        // Step 5: Print metrics
//        System.out.printf("Successful Transactions: %d%n", successfulTransactions.get());
//        System.out.printf("Retry Count: %d%n", retryCount.get());
//    }
//}
