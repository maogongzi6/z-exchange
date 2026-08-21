-- Every run uses new identities. This avoids reading stale snapshot cache data
-- and prevents one smoke run's asynchronous ledger reply from affecting another.
SET @asset_id = 'k6-wallet-smoke-asset';
SET @out_wallet_id = CONCAT('k6-ws-out-wallet-', @smoke_run_id);
SET @in_wallet_id = CONCAT('k6-ws-in-wallet-', @smoke_run_id);
SET @out_wallet_ref = CONCAT('k6-ws-out-ref-', @smoke_run_id);
SET @in_wallet_ref = CONCAT('k6-ws-in-ref-', @smoke_run_id);
SET @out_account_ref = CONCAT('k6-ws-out-ref-', @smoke_run_id);
SET @in_account_ref = CONCAT('k6-ws-in-ref-', @smoke_run_id);

INSERT INTO wallets (
    wallet_id,
    service_id,
    reference_id,
    asset_id,
    wallet_status,
    owner_type,
    owner_id
)
VALUES (
    @out_wallet_id,
    2,
    @out_wallet_ref,
    @asset_id,
    2,
    2,
    CONCAT('k6-ws-owner-', @smoke_run_id)
), (
    @in_wallet_id,
    2,
    @in_wallet_ref,
    @asset_id,
    2,
    2,
    CONCAT('k6-ws-owner-', @smoke_run_id)
);

INSERT INTO balance_snapshots (
    wallet_id,
    service_id,
    wallet_reference_id,
    asset_id,
    wallet_status,
    owner_type,
    owner_id,
    available,
    reserved,
    last_txn_id,
    version,
    created_at,
    updated_at
)
SELECT
    wallet_id,
    service_id,
    reference_id,
    asset_id,
    wallet_status,
    owner_type,
    owner_id,
    CASE WHEN wallet_id = @out_wallet_id THEN @initial_balance ELSE 0 END,
    0,
    NULL,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM wallets
WHERE wallet_id IN (@out_wallet_id, @in_wallet_id);

INSERT INTO wallet_account_mappings (wallet_id, account_ref_id)
VALUES
    (@out_wallet_id, @out_account_ref),
    (@in_wallet_id, @in_account_ref);
