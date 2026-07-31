-- Ten assets and one hundred accounts reserved for distributed ledger writes.
-- The derived tables avoid requiring CREATE ROUTINE privileges on the service
-- database user while keeping fixture provisioning deterministic and idempotent.
INSERT INTO assets (asset_id, asset_type, symbol, decimals)
SELECT
    fixture.asset_id,
    fixture.asset_type,
    fixture.symbol,
    fixture.decimals
FROM (
    SELECT
        CONCAT('k6-write-asset-', LPAD(digit.n, 2, '0')) AS asset_id,
        1 AS asset_type,
        CONCAT('K6W', LPAD(digit.n, 2, '0')) AS symbol,
        2 AS decimals
    FROM (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) AS digit
) AS fixture
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
SELECT
    fixture.account_id,
    fixture.service_id,
    fixture.reference_id,
    fixture.category,
    fixture.normal_side,
    fixture.owner_id,
    fixture.owner_type,
    fixture.asset_id,
    fixture.account_status
FROM (
    SELECT
        CONCAT('k6-write-account-id-', LPAD(tens.n * 10 + ones.n, 3, '0')) AS account_id,
        2 AS service_id,
        CONCAT('k6-write-account-', LPAD(tens.n * 10 + ones.n, 3, '0')) AS reference_id,
        1 AS category,
        IF(MOD(tens.n * 10 + ones.n, 2) = 0, 1, 2) AS normal_side,
        CONCAT('k6-write-owner-', LPAD(tens.n * 10 + ones.n, 3, '0')) AS owner_id,
        2 AS owner_type,
        CONCAT('k6-write-asset-', LPAD(tens.n, 2, '0')) AS asset_id,
        1 AS account_status
    FROM (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) AS tens
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) AS ones
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
