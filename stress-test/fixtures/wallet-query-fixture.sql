-- Required session variables:
--   @run_id, @fixture_size, @system_percent

DROP TEMPORARY TABLE IF EXISTS k6_query_number;
CREATE TEMPORARY TABLE k6_query_number (n int NOT NULL PRIMARY KEY);

INSERT INTO k6_query_number (n)
SELECT number_value
FROM (
    SELECT
        ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000
        + ten_thousands.n * 10000 + hundred_thousands.n * 100000
        + millions.n * 1000000 AS number_value
    FROM
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ten_thousands
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundred_thousands
        CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) millions
) generated_numbers
WHERE number_value < @fixture_size;

INSERT INTO wallets (
    wallet_id, service_id, reference_id, asset_id, wallet_status, owner_type, owner_id
)
SELECT
    CONCAT('qw:', @run_id, ':', LPAD(n, 7, '0')),
    2,
    CONCAT('qr:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('qa:', @run_id),
    2,
    CASE WHEN MOD(n, 100) < @system_percent THEN 1 ELSE 2 END,
    CONCAT('qo:', @run_id, ':', LPAD(n, 7, '0'))
FROM k6_query_number;

INSERT INTO balance_snapshots (
    wallet_id, service_id, wallet_reference_id, asset_id, wallet_status,
    owner_type, owner_id, available, reserved, last_txn_id, version,
    created_at, updated_at
)
SELECT
    wallet_id,
    service_id,
    reference_id,
    asset_id,
    wallet_status,
    owner_type,
    owner_id,
    1000000 + CAST(SUBSTRING_INDEX(wallet_id, ':', -1) AS UNSIGNED),
    0,
    NULL,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM wallets
WHERE wallet_id LIKE CONCAT('qw:', @run_id, ':%');

DROP TEMPORARY TABLE k6_query_number;
