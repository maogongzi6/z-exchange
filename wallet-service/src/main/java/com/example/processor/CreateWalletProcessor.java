package com.example.processor;

import com.example.client.AccountServiceClient;
import com.example.dao.manager.BalanceSnapshotManager;
import com.example.dao.manager.WalletAccountMappingManager;
import com.example.dao.manager.WalletManager;
import com.example.dao.mapper.BalanceSnapshotMapper;
import com.example.dao.mapper.WalletAccountMappingMapper;
import com.example.dao.mapper.WalletMapper;
import com.example.exception.CommonException;
import com.example.exception.ErrorCode;
import com.example.exception.WalletException;
import com.example.po.enums.OwnerType;
import com.example.po.enums.ServiceId;
import com.example.po.enums.WalletStatus;
import com.example.po.wallet.BalanceSnapshot;
import com.example.po.wallet.Wallet;
import com.example.po.wallet.WalletAccountMapping;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.pojo.wallet.CreateWalletReply;
import com.example.pojo.wallet.CreateWalletRequest;
import com.example.proto.ledger.account.CreateAccountReplyPb;
import com.example.proto.ledger.account.CreateAccountRequestPb;
import com.example.proto.ledger.common.AccountCategoryPb;
import com.example.proto.ledger.common.NormalSidePb;
import com.example.proto.ledger.common.ServiceIdPb;
import com.example.utils.EnumConvertHelper;
import com.example.utils.IdGenerator;
import com.example.utils.LedgerServiceHelper;
import com.example.utils.WalletHelper;
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

    public CreateWalletReply createWallet(CreateWalletRequest req) {
        ServiceId serviceId = EnumConvertHelper.serviceIdPbToPo(req.getServiceId());
        OwnerType ownerType = EnumConvertHelper.ownerTypePbToPo(req.getOwnerType());
        validateReq(req, serviceId, ownerType);

        Wallet walletInDb = walletMapper.selectByReferenceId(serviceId, req.getReferenceId());
        if (walletInDb != null && WalletHelper.hasInitiated(walletInDb.getWalletStatus())) {
            return new CreateWalletReply(ErrorCode.SUCCESS.code, "wallet has been created");
        }

        if (walletInDb == null) {
            walletInDb = createWalletInDb(req, serviceId, ownerType);
        }
        createAccount(walletInDb);
        enableWallet(walletInDb);

        return new CreateWalletReply(ErrorCode.SUCCESS.code, ErrorCode.SUCCESS.message);
    }

    private void validateReq(CreateWalletRequest req, ServiceId serviceId, OwnerType ownerType) {
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_service_id: " + req.getServiceId());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_owner_type: " + req.getOwnerType());
        }
    }

    private Wallet createWalletInDb(CreateWalletRequest req, ServiceId serviceId, OwnerType ownerType) {
        String walletId = IdGenerator.generateWalletId();
        Wallet wallet = Wallet.create(walletId, serviceId, req.getReferenceId(), req.getAssetId(), WalletStatus.INIT, ownerType, req.getOwnerId());
        // throw duplicated wallet exception here, maybe some other thread is creating the same wallet
        walletManager.insertWithDuplicateException(wallet);
        return wallet;
    }

    private void createAccount(Wallet wallet) {
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
        if (reply.getCode() != ErrorCode.SUCCESS.code) {
            throw WalletException.createAccountFailed();
        }
    }

    // throw duplicated wallet exception here, maybe some other thread is creating the same wallet
    private void enableWallet(Wallet wallet) {
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.execute((transactionStatus) -> {
            try {
                int affected = walletMapper.updateWalletStatus(wallet.getWalletId(), WalletStatus.INIT, WalletStatus.OPEN);
                if (affected == 0) {
                    throw WalletException.enableWalletFailed("unable_to_change_status: " + wallet);
                }
                WalletAccountMapping mapping = WalletAccountMapping.create(wallet.getWalletId(), wallet.getWalletId());
                walletAccountMappingManager.insertWithDuplicateException(mapping);
                BalanceSnapshot snapshot = BalanceSnapshot.create(IdGenerator.generateBalanceSnapshotId(), wallet.getWalletId(), wallet.getAssetId(), 0L, 0L);
                balanceSnapshotManager.insertWithDuplicateException(snapshot);
            } catch (Exception e) {
                transactionStatus.setRollbackOnly();
                throw e;
            }
            return null;
        });
    }
}
