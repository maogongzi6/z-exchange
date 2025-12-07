package com.example.processor.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dao.manager.AccountManager;
import com.example.dao.mapper.AccountMapper;
import com.example.dao.mapper.AssetMapper;
import com.example.exception.*;
import com.example.po.enums.*;
import com.example.po.account.*;
import com.example.po.asset.Asset;
import com.example.pojo.ledger.account.CreateAccountReply;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.utils.EnumConvertHelper;
import com.example.utils.IdGenerator;
import com.example.utils.ValidateHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateAccountProcessor {
    final private AccountManager accountManager;
    final private AssetMapper assetMapper;

    public CreateAccountReply createAccount(CreateAccountRequest req) {
        ServiceId serviceId = EnumConvertHelper.serviceIdPbToPo(req.getServiceId());
        AccountCategory category = EnumConvertHelper.accountCategoryPbToPo(req.getCategory());
        NormalSide normalSide = EnumConvertHelper.normalSidePbToPo(req.getNormalSide());
        OwnerType ownerType = EnumConvertHelper.ownerTypePbToPo(req.getOwnerType());
        validateReq(req, serviceId, category, normalSide, ownerType);

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Asset::getAssetId).eq(Asset::getAssetId, req.getAssetId());
        List<Asset> asset = assetMapper.selectList(wrapper);
        wrapper.clear();

        if (asset.isEmpty()) {
            throw AssetException.assetNotFound(req.getAssetId());
        }

        String accountId = IdGenerator.generateAccountId();
        try {
            accountManager.insertWithDuplicateException(Account.create(accountId, serviceId, req.getReferenceId(), category, normalSide, req.getOwnerId(), ownerType, req.getAssetId(), AccountStatus.OPEN));
        } catch (AccountException e) {
            if (e.getCode() == ErrorCode.ACCOUNT_DUPLICATED) {
                // return success if exist
                return new CreateAccountReply(ErrorCode.SUCCESS.code, "success, account_already_exist");
            }
            throw e;
        }
        return new CreateAccountReply(ErrorCode.SUCCESS.code, ErrorCode.SUCCESS.message);
    }

    private void validateReq(CreateAccountRequest req, ServiceId serviceId, AccountCategory category, NormalSide normalSide, OwnerType ownerType) {
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_service_id: " + req.getServiceId());
        }
        if (category == null || category == AccountCategory.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_category: " + req.getCategory());
        }
        if (normalSide == null || normalSide == NormalSide.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_normal_side: " + req.getNormalSide());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_owner_type: " + req.getOwnerType());
        }

        if (!ValidateHelper.validateNormalSideAndCategory(normalSide, category)) {
            throw CommonException.invalidRequestParameter("invalid_normal_side_category_pair: " + req.getNormalSide());
        }
    }
}
