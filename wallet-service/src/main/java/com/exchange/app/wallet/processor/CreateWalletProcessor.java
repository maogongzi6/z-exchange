package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.client.AccountServiceClient;
import com.exchange.app.wallet.dao.manager.BalanceSnapshotManager;
import com.exchange.app.wallet.dao.manager.WalletAccountMappingManager;
import com.exchange.app.wallet.dao.manager.WalletManager;
import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.exception.CommonException;
import com.exchange.app.wallet.exception.ErrorCode;
import com.exchange.app.wallet.exception.WalletException;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.po.wallet.Wallet;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.utils.EnumConvertHelper;
import com.exchange.app.wallet.utils.IdGenerator;
import com.exchange.app.wallet.utils.LedgerServiceHelper;
import com.exchange.app.wallet.utils.WalletHelper;
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
        ServiceId serviceId = EnumConvertHelper.serviceIdPbToPo(req.getServiceId());
        OwnerType ownerType = EnumConvertHelper.ownerTypePbToPo(req.getOwnerType());
        validateReq(req, serviceId, ownerType);

        Wallet walletInDb = walletMapper.selectByReferenceId(serviceId, req.getReferenceId());
        if (walletInDb != null && WalletHelper.hasInitiated(walletInDb.getWalletStatus())) {
            return CreateWalletReplyPb.newBuilder().setCode(ErrorCode.SUCCESS.code).setMsg("wallet has been created").build();
        }

        if (walletInDb == null) {
            walletInDb = createWalletInDb(req, serviceId, ownerType);
        }
        createAccount(walletInDb);
        enableWallet(walletInDb);

        return CreateWalletReplyPb.newBuilder().setCode(ErrorCode.SUCCESS.code).setMsg(ErrorCode.SUCCESS.message).build();
    }

    private void validateReq(CreateWalletRequestPb req, ServiceId serviceId, OwnerType ownerType) {
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_service_id: " + req.getServiceId());
        }
        if (ownerType == null || ownerType == OwnerType.UNKNOWN) {
            throw CommonException.invalidRequestParameter("invalid_owner_type: " + req.getOwnerType());
        }
    }

    private Wallet createWalletInDb(CreateWalletRequestPb req, ServiceId serviceId, OwnerType ownerType) {
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
