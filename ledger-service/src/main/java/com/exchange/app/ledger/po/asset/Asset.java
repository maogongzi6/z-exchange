package com.exchange.app.ledger.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.ledger.po.enums.AssetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("assets")
public class Asset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetId;
    private AssetType assetType;
    private String symbol;
    private int decimals;
    private String extraData;

    public static Asset create(String assetId, AssetType assetType, String symbol, int decimals, String extraData) {
        return new Asset(0L, assetId, assetType, symbol, decimals, extraData);
    }
}
