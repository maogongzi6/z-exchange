package com.exchange.app.ledger.dao.repository;

import com.exchange.app.ledger.dao.mapper.AssetMapper;
import com.exchange.app.ledger.po.asset.Asset;
import com.exchange.common.db.manager.DbBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AssetRepository extends DbBaseRepository<Asset, AssetMapper> {
    @Autowired
    public AssetRepository(AssetMapper mapper) {
        super(mapper);
    }

    public Asset getByAssetId(String assetId) {
        return mapper.selectOne(queryLambdaWrapper()
                .eq(Asset::getAssetId, assetId));
    }
}
