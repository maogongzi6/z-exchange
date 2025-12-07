package com.example.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.example.config.com.example.processor.PostLedgerProcessorConfig;
import com.example.dao.mapper.AccountMapper;
import com.example.dao.mapper.AssetMapper;
import com.example.dao.mapper.LedgerEntryMapper;
import com.example.dao.mapper.LedgerTxnMapper;
import com.example.po.ledger.LedgerTxn;
import com.example.pojo.ledger.post.LedgerEntry;
import com.example.pojo.ledger.post.PostTransactionReply;
import com.example.pojo.ledger.post.PostTransactionRequest;
import com.example.processor.post.PostLedgerProcessor;
import com.example.proto.ledger.common.LedgerDirectionPb;
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
//@ContextConfiguration(classes={PostLedgerProcessorConfig.class})
public class PostLedgerProcessorTest {
    @Autowired
    PostLedgerProcessor postLedgerProcessor;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private LedgerEntryMapper ledgerEntryMapper;
    @Autowired
    private LedgerTxnMapper ledgerTxnMapper;

    @Test
    public void testPostTxnSuccess() {
        String refId = "txn-6";
        PostTransactionRequest request = new PostTransactionRequest("", refId, "description", List.of(
                new LedgerEntry("account-1", LedgerDirectionPb.LedgerDirection_Debit, 100, "asset-1"),
                new LedgerEntry("account-2", LedgerDirectionPb.LedgerDirection_Credit, 100, "asset-1")
        ));
        PostTransactionReply reply = postLedgerProcessor.postTransaction(request);
        Assert.assertEquals(0, reply.getCode());

        LambdaQueryWrapper<LedgerTxn> queryWrapper = (new LambdaQueryWrapper<LedgerTxn>()).eq(LedgerTxn::getReferenceId, refId);
        List<LedgerTxn> txn = ledgerTxnMapper.selectList(queryWrapper);
        Assert.assertEquals(1, txn.size());

        LambdaQueryWrapper<com.example.po.ledger.LedgerEntry> entryWrapper = new LambdaQueryWrapper<>();
        entryWrapper.eq(com.example.po.ledger.LedgerEntry::getTxnId, txn.get(0).getTxnId());
        List<com.example.po.ledger.LedgerEntry> entries = ledgerEntryMapper.selectList(entryWrapper);
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(txn.get(0).getTxnId(), entries.get(0).getTxnId());
        Assert.assertEquals(100, entries.get(0).getAmount());
        Assert.assertEquals(txn.get(0).getTxnId(), entries.get(1).getTxnId());
        Assert.assertEquals(100, entries.get(1).getAmount());

        log.info("txn: {}", txn);
        log.info("entry: {}", entries);
    }
}
