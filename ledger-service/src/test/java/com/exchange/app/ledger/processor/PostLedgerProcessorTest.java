package com.exchange.app.ledger.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.example.config.com.exchange.app.ledger.processor.PostLedgerProcessorConfig;
import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.dao.mapper.AssetMapper;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.processor.post.PostLedgerProcessor;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
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
        String refId = "txn-a-12";
        PostTransactionRequestPb request = PostTransactionRequestPb.newBuilder().setReferenceId(refId).setDescription("description").addAllEntries(List.of(
                LedgerEntryPb.newBuilder().setAccountRef("5367025801-1").setDirection(LedgerDirectionPb.LedgerDirection_Debit).setAmount(100).setAssetId("asset-1").build(),
                LedgerEntryPb.newBuilder().setAccountRef("5367025801-1").setDirection(LedgerDirectionPb.LedgerDirection_Credit).setAmount(100).setAssetId("asset-1").build()
        )).build();
        PostTransactionReplyPb reply = postLedgerProcessor.postTransaction(request);

        log.info("reply: {}", reply);
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());

        LambdaQueryWrapper<LedgerTxn> queryWrapper = (new LambdaQueryWrapper<LedgerTxn>()).eq(LedgerTxn::getReferenceId, refId);
        List<LedgerTxn> txn = ledgerTxnMapper.selectList(queryWrapper);
        Assert.assertEquals(1, txn.size());

        LambdaQueryWrapper<LedgerEntry> entryWrapper = new LambdaQueryWrapper<>();
        entryWrapper.eq(LedgerEntry::getTxnId, txn.get(0).getTxnId());
        List<LedgerEntry> entries = ledgerEntryMapper.selectList(entryWrapper);
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(txn.get(0).getTxnId(), entries.get(0).getTxnId());
        Assert.assertEquals(100, entries.get(0).getAmount());
        Assert.assertEquals(txn.get(0).getTxnId(), entries.get(1).getTxnId());
        Assert.assertEquals(100, entries.get(1).getAmount());

        log.info("txn: {}", txn);
        log.info("entry: {}", entries);
        log.info("reply: {}", reply);
    }
}
