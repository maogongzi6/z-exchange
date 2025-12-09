package com.exchange.app.ledger.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.processor.account.CreateAccountProcessor;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.proto.ledger.common.AccountCategoryPb;
import com.exchange.proto.ledger.common.NormalSidePb;
import com.exchange.proto.ledger.common.OwnerTypePb;
import com.exchange.proto.ledger.common.ServiceIdPb;
import com.exchange.app.ledger.utils.EnumMappers;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class CreateAccountProcessorTest {
    @Autowired
    CreateAccountProcessor createAccountProcessor;
    @Autowired
    private AccountMapper accountMapper;

    @Test
    public void testCreateAccount() {
        String assetId = "1";
        ServiceIdPb serviceId = ServiceIdPb.ServiceIdPb_Wallet;
        String refId = "account-test-12";
        CreateAccountRequestPb request = CreateAccountRequestPb.newBuilder().setReferenceId(refId).
                setServiceId(serviceId).setAssetId(assetId).setCategory(AccountCategoryPb.AccountCategory_Asset).
                setNormalSide(NormalSidePb.NormalSidePb_Debit).setOwnerType(OwnerTypePb.OwnerTypePb_User).setOwnerId("owner").build();
        CreateAccountReplyPb reply = createAccountProcessor.createAccount(request);
        log.info(reply.toString());
        LambdaQueryWrapper<Account> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Account::getReferenceId).eq(Account::getServiceId, EnumMappers.serviceIdPbMapper.to(serviceId)).eq(Account::getReferenceId, refId);
        List<Account> list = accountMapper.selectList(queryWrapper);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals(refId, list.get(0).getReferenceId());
    }
}
