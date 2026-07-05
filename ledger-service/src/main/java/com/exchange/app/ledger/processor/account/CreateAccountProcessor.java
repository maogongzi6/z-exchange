package com.exchange.app.ledger.processor.account;

import com.exchange.app.ledger.dao.repository.AccountRepository;
import com.exchange.app.ledger.dao.repository.AssetRepository;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.asset.Asset;
import com.exchange.app.ledger.result.LedgerBoundaryErrorMapper;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.common.result.IResult;
import com.exchange.common.result.Result;
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

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateAccountProcessor {
    final private AccountRepository accountManager;
    final private AssetRepository assetRepository;

    public CreateAccountReplyPb createAccount(CreateAccountRequestPb req) {

        ServiceId serviceId = EnumMappers.serviceIdPbMapper.to(req.getServiceId());
        AccountCategory category = EnumMappers.accountCategoryPbMapper.to(req.getCategory());
        NormalSide normalSide = EnumMappers.normalSidePbMapper.to(req.getNormalSide());
        OwnerType ownerType = EnumMappers.ownerTypePbMapper.to(req.getOwnerType());

        Result<Void> result = validateReq(req, serviceId, category, normalSide, ownerType);
        if (!result.isSuccess()) {
            return replyError(result);
        }

        Asset asset = assetRepository.getByAssetId(req.getAssetId());
        if (asset == null) {
            return replyError(LedgerServiceErrorCode.ASSET_NOT_FOUND, req.getAssetId());
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
            return Result.failure(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, "invalid_service_id: " + req.getServiceId());
        }
        if (category == null || category == AccountCategory.UNKNOWN) {
            return Result.failure(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, "invalid_category: " + req.getCategory());
        }
        if (normalSide == null || normalSide == NormalSide.UNKNOWN) {
            return Result.failure(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, "invalid_normal_side: " + req.getNormalSide());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            return Result.failure(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, "invalid_owner_type: " + req.getOwnerType());
        }

        if (!ValidateHelper.validateNormalSideAndCategory(normalSide, category)) {
            return Result.failure(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, "invalid_normal_side_category_pair: " + req.getNormalSide());
        }

        return Result.success();
    }

    private CreateAccountReplyPb replySuccess(String detail) {
        return CreateAccountReplyPb.newBuilder()
                .setError(PbErrorBuilder.success(detail))
                .build();
    }

    private CreateAccountReplyPb replyError(LedgerServiceErrorCode errorCode, String detail) {
        CreateAccountReplyPb.Builder builder = CreateAccountReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private CreateAccountReplyPb replyError(IResult<?> result) {
        Objects.requireNonNull(result);
        LedgerServiceErrorCode errorCode = LedgerBoundaryErrorMapper.toLedgerErrorCode(result);
        return CreateAccountReplyPb.newBuilder()
                .setError(PbErrorBuilder.build(errorCode, result.getDetail()))
                .build();
    }
}
