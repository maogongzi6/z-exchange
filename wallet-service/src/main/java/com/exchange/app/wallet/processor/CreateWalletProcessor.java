package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.client.AccountServiceClient;
import com.exchange.app.wallet.dao.manager.BalanceSnapshotManager;
import com.exchange.app.wallet.dao.manager.WalletAccountMappingManager;
import com.exchange.app.wallet.dao.manager.WalletManager;
import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.po.wallet.Wallet;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.*;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import com.exchange.proto.ledger.common.AccountCategoryPb;
import com.exchange.proto.ledger.common.NormalSidePb;
import com.exchange.proto.ledger.common.ServiceIdPb;
import com.exchange.proto.wallet.wallet.CreateWalletReplyPb;
import com.exchange.proto.wallet.wallet.CreateWalletRequestPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CreateWalletProcessor {
    final private TransactionTemplate transactionTemplate;

    final private WalletMapper walletMapper;
    final private BalanceSnapshotManager balanceSnapshotManager;
    final private WalletAccountMappingManager walletAccountMappingManager;

    final private WalletManager walletManager;

    final private AccountServiceClient accountServiceClient;

    public CreateWalletReplyPb createWallet(CreateWalletRequestPb req) {
        ServiceId serviceId = EnumMappers.serviceIdPbMapper.to(req.getServiceId());
        OwnerType ownerType = EnumMappers.ownerTypePbMapper.to(req.getOwnerType());
        Result<Void> result = validateReq(req, serviceId, ownerType);
        if (!result.success) {
            return replyError(result);
        }

        Wallet walletInDb = walletMapper.selectByReferenceId(serviceId, req.getReferenceId());
        if (walletInDb != null && walletInDb.getWalletStatus().hasInitiated()) {
            return replySuccess("wallet has been created");
        } else if (walletInDb == null) {
            Result<Wallet> walletResult = createWalletInDb(req, serviceId, ownerType);
            if (!walletResult.success) {
                return replyError(walletResult);
            }
            walletInDb = walletResult.value;
        }

        result = createAccount(walletInDb);
        if (!result.success) {
           return replyError(result);
        }

        result = enableWallet(walletInDb);
        if (!result.success) {
            return replyError(result);
        }

        return replySuccess("success");
    }

    private Result<Void> validateReq(CreateWalletRequestPb req, ServiceId serviceId, OwnerType ownerType) {
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER,"invalid_service_id: " + req.getServiceId());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER,"invalid_owner_type: " + req.getOwnerType());
        }
        return Result.success(null);
    }

    private Result<Wallet> createWalletInDb(CreateWalletRequestPb req, ServiceId serviceId, OwnerType ownerType) {
        String walletId = IdGenerator.generateWalletId();
        Wallet wallet = Wallet.create(walletId, serviceId, req.getReferenceId(), req.getAssetId(), WalletStatus.INIT, ownerType, req.getOwnerId());
        // return duplicated wallet error here, maybe some other thread is creating the same wallet
        if (walletManager.insertIgnore(wallet) == 0) {
            return Result.fail(ErrorCode.WALLET_DUPLICATED, wallet.toString());
        }
        return Result.success(wallet);
    }

    private Result<Void> createAccount(Wallet wallet) {
        CreateAccountRequestPb req = CreateAccountRequestPb.newBuilder()
                .setServiceId(ServiceIdPb.ServiceIdPb_Wallet)
                .setReferenceId(wallet.getWalletId())
                .setAssetId(wallet.getAssetId())
                .setCategory(AccountCategoryPb.AccountCategory_Liability)
                .setNormalSide(NormalSidePb.NormalSidePb_Credit)
                .setOwnerType(LedgerServiceHelper.ownerTypeToLedgerPb(wallet.getOwnerType()))
                .setOwnerId(wallet.getOwnerId())
                .build();
        CreateAccountReplyPb reply = accountServiceClient.createAccount(req);
        if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            return Result.fail(ErrorCode.CREATE_ACCOUNT_FAILED, reply.getError().getMessage());
        }
        return Result.success();
    }

    // return fail for duplicated error code, another thread could be operating the same wallet
    private Result<Void> enableWallet(Wallet wallet) {
        return DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            if (walletMapper.updateWalletStatus(wallet.getWalletId(), WalletStatus.INIT, WalletStatus.OPEN) == 0) {
                return Result.fail(ErrorCode.WALLET_UPDATE_FAILED, "unable_to_change_status: " + wallet);
            }
            WalletAccountMapping mapping = WalletAccountMapping.create(wallet.getWalletId(), wallet.getWalletId());
            if (walletAccountMappingManager.insertIgnore(mapping) == 0) {
                return Result.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_DUPLICATED, "unexpected duplicate: " + mapping);
            }
            BalanceSnapshot snapshot = BalanceSnapshot.create(wallet.getWalletId(), wallet.getServiceId(), wallet.getReferenceId(), wallet.getAssetId(), WalletStatus.OPEN, wallet.getOwnerType(), wallet.getOwnerId(), 0L, 0L);
            if (balanceSnapshotManager.insertIgnore(snapshot) == 0) {
                return Result.fail(ErrorCode.BALANCE_SNAPSHOT_DUPLICATED, "unexpected duplicate: " + snapshot);
            }
            return Result.success();
        });
    }

    private CreateWalletReplyPb replySuccess(String detail) {
        return replyError(ErrorCode.SUCCESS, detail);
    }

    private CreateWalletReplyPb replyError(ErrorCode errorCode, String detail) {
        CreateWalletReplyPb.Builder builder = CreateWalletReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private CreateWalletReplyPb replyError(Result<?> result) {
        result = Result.requireNotNull(result);
        return replyError(result.errorCode, result.errorDetail);
    }
}
