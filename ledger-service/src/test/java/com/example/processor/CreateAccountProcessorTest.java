package com.example.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dao.mapper.AccountMapper;
import com.example.po.account.Account;
import com.example.pojo.ledger.account.CreateAccountReply;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.processor.account.CreateAccountProcessor;
import com.example.proto.ledger.common.AccountCategoryPb;
import com.example.proto.ledger.common.NormalSidePb;
import com.example.proto.ledger.common.OwnerTypePb;
import com.example.proto.ledger.common.ServiceIdPb;
import com.example.utils.EnumConvertHelper;
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
        String refId = "account-test-9";
        CreateAccountRequest request = new CreateAccountRequest(refId, serviceId, assetId, AccountCategoryPb.AccountCategory_Asset, NormalSidePb.NormalSidePb_Debit, OwnerTypePb.OwnerTypePb_User, "owner");
        CreateAccountReply reply = createAccountProcessor.createAccount(request);
        log.info(reply.toString());
        LambdaQueryWrapper<Account> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Account::getAccountId).eq(Account::getServiceId, EnumConvertHelper.serviceIdPbToPo(serviceId)).eq(Account::getReferenceId, refId);
        List<Account> list = accountMapper.selectList(queryWrapper);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals(refId, list.get(0).getReferenceId());
    }
}
