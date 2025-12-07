package com.example.utils;

import com.example.po.enums.AccountCategory;
import com.example.po.enums.NormalSide;

import java.util.HashMap;
import java.util.Map;

public class ValidateHelper {
    static private Map<AccountCategory, NormalSide> categoryNormalSidePairs = new HashMap<>() {{
        put(AccountCategory.ASSET, NormalSide.DEBIT);
        put(AccountCategory.LIABILITY, NormalSide.CREDIT);
    }};

    static public boolean validateNormalSideAndCategory(NormalSide normalSide, AccountCategory accountCategory) {
        return categoryNormalSidePairs.get(accountCategory) == normalSide;
    }
}
