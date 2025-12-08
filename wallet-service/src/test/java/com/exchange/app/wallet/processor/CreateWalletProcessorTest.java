package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.exception.ErrorCode;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.Wallet;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.common.OwnerTypePb;
import com.exchange.proto.wallet.wallet.CreateWalletReplyPb;
import com.exchange.proto.wallet.wallet.CreateWalletRequestPb;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class CreateWalletProcessorTest {
    @Autowired
    CreateWalletProcessor createWalletProcessor;
    @Autowired
    WalletMapper walletMapper;

    @Test
    public void testCreateWalletProcessor() {
        String ref = "create-wallet-test-6";
        String asset = "1";
        CreateWalletRequestPb request = CreateWalletRequestPb.newBuilder()
                .setReferenceId(ref)
                .setAssetId(asset)
                .setServiceId(ServiceIdPb.ServiceIdPb_User)
                .setOwnerId("sun")
                .setOwnerType(OwnerTypePb.OwnerTypePb_User).build();

        CreateWalletReplyPb reply = createWalletProcessor.createWallet(request);
        Assert.assertEquals(ErrorCode.SUCCESS.code, reply.getCode());
        Wallet wallet = walletMapper.selectByReferenceId(ServiceId.USER, ref);
        Assert.assertEquals(WalletStatus.OPEN, wallet.getWalletStatus());
        System.out.println(reply);
    }
}
