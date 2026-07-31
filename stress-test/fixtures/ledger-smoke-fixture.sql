-- These rows are reserved for k6 and can be safely reused across smoke runs.
-- Upserts make fixture provisioning idempotent while also repairing a partially
-- or incorrectly provisioned test fixture.
INSERT INTO assets (asset_id, asset_type, symbol, decimals)
VALUES ('k6-smoke-asset', 1, 'K6-SMOKE', 2) AS fixture
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
    'k6-smoke-account-id',
    2,
    'k6-smoke-account',
    1,
    1,
    'k6-smoke-owner',
    2,
    'k6-smoke-asset',
    1
) AS fixture
ON DUPLICATE KEY UPDATE
    service_id = fixture.service_id,
    reference_id = fixture.reference_id,
    category = fixture.category,
    normal_side = fixture.normal_side,
    owner_id = fixture.owner_id,
    owner_type = fixture.owner_type,
    asset_id = fixture.asset_id,
    account_status = fixture.account_status;
