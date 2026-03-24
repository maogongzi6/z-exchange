package com.exchange.app.ledger.processor.post;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.dao.repository.LedgerEntryRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.dao.store.LedgerTxnStore;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.common.utils.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class GetLedgerTxnProcessorIntegrationTest {

    @Autowired
    private LedgerTxnRepository ledgerTxnRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private GetLedgerTxnProcessor getLedgerTxnProcessor;
    @Autowired
    private LedgerTxnMapper ledgerTxnMapper;
    @Autowired
    private LedgerEntryMapper ledgerEntryMapper;

    String id = "FIXED_ID_11";
    @Autowired
    private LedgerTxnStore ledgerTxnStore;

    @Test
    void shouldReturnSuccessWhenLookupByTxnIdAndIncludeEntries() throws InterruptedException {

        // Arrange
        //String txnId = "TXN_TEST_" + System.currentTimeMillis() + "_" + Math.random();
        String txnId = id;
        String refId = "REF_TEST_" + System.currentTimeMillis() + "_" + Math.random();
        ledgerTxnMapper.delete(Wrappers.<LedgerTxn>lambdaQuery().eq(LedgerTxn::getTxnId, txnId));
        LedgerTxn txn = LedgerTxn.create(txnId, refId, "{\"k\":\"v\"}");
        ledgerTxnRepository.insertIgnore(txn);

        ledgerEntryMapper.delete(Wrappers.<LedgerEntry>lambdaQuery().eq(LedgerEntry::getTxnId, txnId));
        LedgerEntry entry = LedgerEntry.create("ENTRY_TEST_" + System.currentTimeMillis() + "_" + Math.random(), txnId, "ASSET_001", "ACCOUNT_001", 100, Direction.DEBIT);
        ledgerEntryRepository.insertIgnore(entry);

        // Act
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result =
                getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, txnId, true);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.success).isTrue();

        GetLedgerTxnProcessor.LedgerTxnInfo info = result.value;
        assertThat(info).isNotNull();
        assertThat(info.txn).isNotNull();
        assertThat(info.txn.getTxnId()).isEqualTo(txnId);

        assertThat(info.entries).isNotNull();
        assertThat(info.entries).isNotEmpty();

        // update to set tombstone
        result.value.txn.setMetadata("{\"k\":\"updated\"}");
        ledgerTxnStore.updateMetadataByPk(result.value.txn);

        // get again, check and load cache
        result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, txnId, true);

    }

    @Test
    void shouldReturnSuccessWhenLookupByRefIdWithMultipleEntries() {
        // Arrange
        //String refId = "REF_TEST_" + System.currentTimeMillis(); // unique reference ID
        //        String txnId = "TXN_TEST_" + System.currentTimeMillis(); // unique transaction ID
        String refId = "FIXED_REF_TEST_12";
        String txnId = "FIXED_TXN_TEST_12";
        ledgerTxnMapper.delete(Wrappers.<LedgerTxn>lambdaQuery().eq(LedgerTxn::getTxnId, txnId));
        LedgerTxn txn = LedgerTxn.create(txnId, refId, "{\"k\":\"v\"}");
        ledgerTxnRepository.insertIgnore(txn);

        ledgerEntryMapper.delete(Wrappers.<LedgerEntry>lambdaQuery().eq(LedgerEntry::getTxnId, txnId));
        for (int i = 1; i <= 5; i++) {
            LedgerEntry entry = LedgerEntry.create(
                    "ENTRY_TEST_" + System.currentTimeMillis() + "_" + i, txnId, "ASSET_00" + i, "ACCOUNT_00" + i, i * 100, Direction.DEBIT
            );
            ledgerEntryRepository.insertIgnore(entry);
        }

        // Act
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result =
                getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, refId, true);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.success).isTrue();

        GetLedgerTxnProcessor.LedgerTxnInfo info = result.value;
        assertThat(info).isNotNull();
        assertThat(info.txn).isNotNull();
        assertThat(info.txn.getReferenceId()).isEqualTo(refId);

        assertThat(info.entries).isNotNull();
        assertThat(info.entries).hasSize(5);

        // update to set tombstone
        result.value.txn.setMetadata("{\"k\":\"updated\"}");
        ledgerTxnStore.updateMetadataByPk(result.value.txn);

        // Act
        result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, refId, true);
    }

    @Test
    void shouldSetNegativeWhenLookupByNotExistRefId() {
        String refId = "FIXED_REF_TEST_NOT_EXIST";
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result =
                getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, refId, true);
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.success).isFalse();
        assertThat(result.errorCode).isEqualTo(ErrorCode.LEDGER_NOT_FOUND);
        assertThat(result.value).isNull();
        result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, refId, true);

    }

}