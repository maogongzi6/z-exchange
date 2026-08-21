-- The wallet smoke test posts one atomic transfer through Kafka to ledger.
-- Create matching ledger accounts so that the asynchronous reply represents a
-- real successful journal write instead of only testing wallet-side acceptance.
SET @asset_id = 'k6-wallet-smoke-asset';
SET @out_account_id = CONCAT('k6-ws-out-account-', @smoke_run_id);
SET @in_account_id = CONCAT('k6-ws-in-account-', @smoke_run_id);
SET @out_account_ref = CONCAT('k6-ws-out-ref-', @smoke_run_id);
SET @in_account_ref = CONCAT('k6-ws-in-ref-', @smoke_run_id);

INSERT INTO assets (asset_id, asset_type, symbol, decimals)
VALUES (@asset_id, 1, 'K6-WALLET-SMOKE', 2) AS fixture
ON DUPLICATE KEY UPDATE
    asset_type = fixture.asset_type,
    symbol = fixture.symbol,
    decimals = fixture.decimals;

INSERT INTO accounts (
    account_id,
    service_id,
    reference_id,
    category,
    normal_side,
    owner_id,
    owner_type,
    asset_id,
    account_status
)
VALUES (
    @out_account_id,
    2,
    @out_account_ref,
    1,
    1,
    CONCAT('k6-ws-owner-', @smoke_run_id),
    2,
    @asset_id,
    1
), (
    @in_account_id,
    2,
    @in_account_ref,
    1,
    2,
    CONCAT('k6-ws-owner-', @smoke_run_id),
    2,
    @asset_id,
    1
);
