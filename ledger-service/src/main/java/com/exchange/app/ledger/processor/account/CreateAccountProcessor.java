package com.exchange.app.ledger.processor.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.dao.manager.AccountManager;
import com.exchange.app.ledger.dao.mapper.AssetMapper;
import com.exchange.app.ledger.exception.*;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.asset.Asset;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.app.ledger.utils.EnumConvertHelper;
import com.exchange.app.ledger.utils.IdGenerator;
import com.exchange.app.ledger.utils.ValidateHelper;
import com.exchange.app.ledger.po.enums.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateAccountProcessor {
    final private AccountManager accountManager;
    final private AssetMapper assetMapper;

    public CreateAccountReplyPb createAccount(CreateAccountRequestPb req) {
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
                return CreateAccountReplyPb.newBuilder().setCode(ErrorCode.SUCCESS.code).setMsg("success, account_already_exist").build();
            }
            throw e;
        }
        return CreateAccountReplyPb.newBuilder().setCode(ErrorCode.SUCCESS.code).setMsg(ErrorCode.SUCCESS.message).build();
    }

    private void validateReq(CreateAccountRequestPb req, ServiceId serviceId, AccountCategory category, NormalSide normalSide, OwnerType ownerType) {
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
