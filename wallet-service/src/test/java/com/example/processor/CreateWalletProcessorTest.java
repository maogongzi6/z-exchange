package com.example.processor;

import com.example.dao.mapper.WalletMapper;
import com.example.exception.ErrorCode;
import com.example.po.enums.ServiceId;
import com.example.po.enums.WalletStatus;
import com.example.po.wallet.BalanceSnapshot;
import com.example.po.wallet.Wallet;
import com.example.pojo.wallet.CreateWalletReply;
import com.example.pojo.wallet.CreateWalletRequest;
import com.example.proto.ledger.common.OwnerTypePb;
import com.example.proto.ledger.common.ServiceIdPb;
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
        String ref = "create-wallet-test-5";
        String asset = "1";
        CreateWalletRequest request = CreateWalletRequest.builder()
                .referenceId(ref)
                .assetId(asset)
                .serviceId(ServiceIdPb.ServiceIdPb_Wallet)
                .ownerId("sun")
                .ownerType(OwnerTypePb.OwnerTypePb_User).build();

        CreateWalletReply reply = createWalletProcessor.createWallet(request);
        Assert.assertEquals(ErrorCode.SUCCESS.code, reply.getCode());
        Wallet wallet = walletMapper.selectByReferenceId(ServiceId.USER, ref);
        Assert.assertEquals(WalletStatus.OPEN, wallet.getWalletStatus());
        System.out.println(reply);
    }
}
