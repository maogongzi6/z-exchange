package com.example.mapper;

import com.example.dao.mapper.AccountMapper;
import com.example.dao.mapper.AssetMapper;
import com.example.dao.mapper.LedgerEntryMapper;
import com.example.dao.mapper.LedgerTxnMapper;
import com.example.po.account.Account;
import com.example.po.asset.Asset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class
MapperTest {
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private LedgerEntryMapper ledgerEntryMapper;
    @Autowired
    private LedgerTxnMapper ledgerTxnMapper;

    @Test
    public void testSelectAccount() {
        List<Account> accounts = accountMapper.selectList(null);
        accounts.forEach(System.out::println);
    }

//    @Test
//    public void testInsertAccount() {
//        Account account = new Account("6", AccountType.NORMAL, "1", OwnerType.NORMAL, "1", "extra");
//        int success = accountMapper.insert(account);
//        System.out.println(success);
//        System.out.println(account);
//    }

    @Test
    public void testSelectAsset() {
        List<Asset> assets = assetMapper.selectList(null);
        assets.forEach(System.out::println);
    }

//    @Test
//    public void testInsertAsset() {
//        Asset asset = new Asset("6", AssetType.NORMAL, "1", 1, "");
//        int success = assetMapper.insert(asset);
//        System.out.println(success);
//        System.out.println(asset);
//    }
}
