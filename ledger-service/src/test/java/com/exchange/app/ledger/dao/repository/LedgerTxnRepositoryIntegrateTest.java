package com.exchange.app.ledger.dao.repository;

import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.client.VersionAppSideCacheReadClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.LocalDateTime;

@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class LedgerTxnRepositoryIntegrateTest {
    @Autowired
    LedgerTxnRepository ledgerTxnRepository;

    @Test
    public void testUpdateVersionControl() {
        String id = LocalDateTime.now().toString();
        // Arrange: Create and save a new LedgerTxn
        LedgerTxn originalTxn = LedgerTxn.create(id, id, "{}");
        ledgerTxnRepository.insertIgnore(originalTxn);

        long originalVersion = originalTxn.getVersion();
        // Act: Retrieve and update the version of the LedgerTxn
        LedgerTxn retrievedTxn = ledgerTxnRepository.getByTxnId(originalTxn.getTxnId());
        retrievedTxn.setMetadata("{\"updated\": true}");
        int affected = ledgerTxnRepository.updateMetadataByPk(retrievedTxn);

        // Assert: Verify the version is updated
        LedgerTxn updatedTxn = ledgerTxnRepository.getByTxnId(originalTxn.getTxnId());
        assert updatedTxn.getVersion() == originalVersion + 1;
        assert affected == 1;
    }
}
