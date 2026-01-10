package com.exchange.upstream.wallet;

import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OwnerTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.wallet.CreateWalletReplyPb;
import com.exchange.proto.wallet.wallet.CreateWalletRequestPb;
import com.exchange.proto.wallet.wallet.WalletServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class WalletTest {
    @Autowired
    CreateOrderProcessor createOrderProcessor;
    @GrpcClient("wallet-client")
    private WalletServiceGrpc.WalletServiceBlockingStub blockingStub;

    ConcurrentHashMap<String, Integer> reservations = new ConcurrentHashMap<>();

    String usdOutWalletRef = "usd-out-wallet", usdInWalletRef = "usd-in-wallet";
    String usdAssetId = "asset-1";
    String owner = "sun";
    OwnerTypePb ownerType = OwnerTypePb.OwnerTypePb_User;
    ServiceIdPb serviceId = ServiceIdPb.ServiceIdPb_User;
    BusinessTypePb businessType = BusinessTypePb.BusinessTypePb_Transfer;
    AtomicInteger transferCounter = new AtomicInteger(0);
    int reserveThreadCount = 50;
    AtomicInteger reserveCountDown = new AtomicInteger(reserveThreadCount);
    AtomicInteger consumeCounter = new AtomicInteger(0);
    @Test
    public void test() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(128);
        long startAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        List<Runnable> tasks = concurrentAtomicTransfer(100, 10000);
        for (Runnable task : tasks) {
            long delay = startAt - System.nanoTime();
            scheduler.schedule(task, delay, TimeUnit.NANOSECONDS);
        }
        while (transferCounter.get() != 100) {}
    }

    private List<Runnable> concurrentAtomicTransfer(int threads, int assetAmount) {
        AtomicInteger a = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    while (a.get() < assetAmount) {
                        int ref = a.incrementAndGet();
                        createOrderProcessor.createDirectTransfer(new CreateOrderProcessor.AssetFlow(usdAssetId, usdOutWalletRef, 1, ""),
                                new CreateOrderProcessor.AssetFlow(usdAssetId, usdInWalletRef, 1, ""), ref + ""
                        );
                    }
                    transferCounter.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            });
        }
        return tasks;
    }

    @Test
    public void testConsume() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(256);
        long startAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        List<Runnable> tasks = concurrentReserve(reserveThreadCount, 10000, 20);
        tasks.addAll(concurrentConsume(100, 15000));
        for (Runnable task : tasks) {
            long delay = startAt - System.nanoTime();
            scheduler.schedule(task, delay, TimeUnit.NANOSECONDS);
        }
        while (consumeCounter.get() != 100) {}
    }

    private List<Runnable> concurrentReserve(int threads, int assetAmount, int reserveEachTime) {
        AtomicInteger a = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    while (a.get() < assetAmount) {
                        int ref = a.addAndGet(reserveEachTime);
                        String reservationRef = createOrderProcessor.createReservation(new CreateOrderProcessor.AssetFlow(usdAssetId, usdOutWalletRef, reserveEachTime, ""), ref + "-r-"+ System.currentTimeMillis()/1000);
                        reservations.put(reservationRef, reserveEachTime+2); // reserveEachTime+2 to test overspend
                        Thread.sleep(100);
                    }
                    reserveCountDown.decrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        }
        return tasks;
    }

    private List<Runnable> concurrentConsume(int threads, int amount) {

        AtomicInteger a = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    while (a.get() < amount) {

                        List<String> keys = new ArrayList<>(reservations.keySet());
                        if (keys.isEmpty()) {
                            continue;
                        }
                        String key = "";
                        for (String k : keys) {
                            if (reservations.get(k) <= 0) {
                                continue;
                            } else {
                                key = k;
                                break;
                            }
                        }
                        if (key.isEmpty()) {
                            continue;
                        }
                        reservations.put(key, reservations.get(key) - 1); // ignore concurrent race
                        int ref = a.incrementAndGet();
                        createOrderProcessor.createConsume(List.of(new CreateOrderProcessor.AssetFlow(usdAssetId, usdOutWalletRef, 1, key)).toArray(new CreateOrderProcessor.AssetFlow[0]), List.of(new CreateOrderProcessor.AssetFlow(usdAssetId, usdInWalletRef, 1, "")).toArray(new CreateOrderProcessor.AssetFlow[]{}), ref + "-c-"+ System.currentTimeMillis()/1000);
                    }

                    consumeCounter.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        }
        return tasks;
    }

    @Test
    public void testCreateWallet() {
        CreateWalletReplyPb reply = blockingStub.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdOutWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = blockingStub.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdInWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
    }
}
