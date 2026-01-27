package com.exchange.app.ledger.processor.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.dao.manager.AccountManager;
import com.exchange.app.ledger.dao.mapper.AssetMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.asset.Asset;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.app.ledger.utils.EnumMappers;
import com.exchange.app.ledger.utils.IdGenerator;
import com.exchange.app.ledger.utils.ValidateHelper;
import com.exchange.app.ledger.po.enums.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateAccountProcessor {
    final private AccountManager accountManager;
    final private AssetMapper assetMapper;

    public CreateAccountReplyPb createAccount(CreateAccountRequestPb req) {

        ServiceId serviceId = EnumMappers.serviceIdPbMapper.to(req.getServiceId());
        AccountCategory category = EnumMappers.accountCategoryPbMapper.to(req.getCategory());
        NormalSide normalSide = EnumMappers.normalSidePbMapper.to(req.getNormalSide());
        OwnerType ownerType = EnumMappers.ownerTypePbMapper.to(req.getOwnerType());

        Result<Void> result = validateReq(req, serviceId, category, normalSide, ownerType);
        if (!result.success) {
            return replyError(result);
        }

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Asset::getAssetId).eq(Asset::getAssetId, req.getAssetId());
        List<Asset> asset = assetMapper.selectList(wrapper);
        wrapper.clear();

        if (asset.isEmpty()) {
            return replyError(ErrorCode.ASSET_NOT_FOUND, req.getAssetId());
        }

        String accountId = IdGenerator.generateAccountId();
        if (accountManager.insertIgnore(Account.create(accountId, serviceId, req.getReferenceId(), category, normalSide, req.getOwnerId(), ownerType, req.getAssetId(), AccountStatus.OPEN))
                == 0) {
            return replySuccess("account already exists");
        }
        return replySuccess("success");
    }

    private Result<Void> validateReq(CreateAccountRequestPb req, ServiceId serviceId, AccountCategory category, NormalSide normalSide, OwnerType ownerType) {
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_service_id: " + req.getServiceId());
        }
        if (category == null || category == AccountCategory.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_category: " + req.getCategory());
        }
        if (normalSide == null || normalSide == NormalSide.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_normal_side: " + req.getNormalSide());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_owner_type: " + req.getOwnerType());
        }

        if (!ValidateHelper.validateNormalSideAndCategory(normalSide, category)) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_normal_side_category_pair: " + req.getNormalSide());
        }

        return Results.success();
    }

    private CreateAccountReplyPb replySuccess(String detail) {
        return replyError(ErrorCode.SUCCESS, detail);
    }

    private CreateAccountReplyPb replyError(ErrorCode errorCode, String detail) {
        CreateAccountReplyPb.Builder builder = CreateAccountReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private CreateAccountReplyPb replyError(Result<?> result) {
        Objects.requireNonNull(result);
        return replyError(Results.getErrorCode(result), result.errorDetail);
    }
}
