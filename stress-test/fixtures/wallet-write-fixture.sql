-- Required session variables:
--   @run_id, @wallet_count, @fixture_requests, @initial_balance,
--   @base_amount, @amount_span, @prepare_reservations

DROP TEMPORARY TABLE IF EXISTS k6_sequence;
CREATE TEMPORARY TABLE k6_sequence (n int NOT NULL PRIMARY KEY);

INSERT INTO k6_sequence (n)
SELECT number_value
FROM (
    SELECT
        ones.n
        + tens.n * 10
        + hundreds.n * 100
        + thousands.n * 1000
        + ten_thousands.n * 10000
        + hundred_thousands.n * 100000
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
WHERE number_value < GREATEST(@wallet_count, @fixture_requests);

INSERT INTO wallets (
    wallet_id, service_id, reference_id, asset_id, wallet_status, owner_type, owner_id
)
SELECT
    CONCAT('ww:', @run_id, ':', LPAD(n, 7, '0')),
    2,
    CONCAT('wr:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('wa:', @run_id),
    2,
    2,
    CONCAT('wo:', @run_id, ':', LPAD(n, 7, '0'))
FROM k6_sequence
WHERE n < @wallet_count;

INSERT INTO balance_snapshots (
    wallet_id, service_id, wallet_reference_id, asset_id, wallet_status,
    owner_type, owner_id, available, reserved, last_txn_id, version,
    created_at, updated_at
)
SELECT
    wallet_id, service_id, reference_id, asset_id, wallet_status,
    owner_type, owner_id, @initial_balance, 0, NULL, 1,
    CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM wallets
WHERE wallet_id LIKE CONCAT('ww:', @run_id, ':%');

INSERT INTO wallet_account_mappings (wallet_id, account_ref_id)
SELECT
    CONCAT('ww:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('ar:', @run_id, ':', LPAD(n, 7, '0'))
FROM k6_sequence
WHERE n < @wallet_count;

-- Apply capacity is measured independently, so its active reservations are
-- prepared before k6 starts instead of calling reserve inside each iteration.
INSERT INTO wallet_transactions (
    txn_id, reference_id, initiator, idempotency_key, txn_status, txn_type,
    business_type, created_at, updated_at
)
SELECT
    CONCAT('wf:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('wf:', @run_id, ':', LPAD(n, 7, '0')),
    2,
    CONCAT('wf:', @run_id, ':', LPAD(n, 7, '0')),
    2,
    2,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM k6_sequence
WHERE @prepare_reservations = 1 AND n < @fixture_requests;

INSERT INTO wallet_reservations (
    reservation_id, wallet_id, wallet_reference_id, asset_id, total, remaining,
    consumed, pending_settle, released, reservation_status,
    reservation_outcome, initiator, reference_id, reserve_txn_id
)
SELECT
    CONCAT('ri:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('ww:', @run_id, ':', LPAD(MOD(n, @wallet_count), 7, '0')),
    CONCAT('wr:', @run_id, ':', LPAD(MOD(n, @wallet_count), 7, '0')),
    CONCAT('wa:', @run_id),
    @base_amount + MOD(n, @amount_span),
    @base_amount + MOD(n, @amount_span),
    0,
    0,
    0,
    1,
    1,
    2,
    CONCAT('rv:', @run_id, ':', LPAD(n, 7, '0')),
    CONCAT('wf:', @run_id, ':', LPAD(n, 7, '0'))
FROM k6_sequence
WHERE @prepare_reservations = 1 AND n < @fixture_requests;

UPDATE balance_snapshots snapshot
JOIN (
    SELECT wallet_id, SUM(total) AS reserved_total
    FROM wallet_reservations
    WHERE reference_id LIKE CONCAT('rv:', @run_id, ':%')
    GROUP BY wallet_id
) fixture_reservation ON fixture_reservation.wallet_id = snapshot.wallet_id
SET
    snapshot.available = @initial_balance - fixture_reservation.reserved_total,
    snapshot.reserved = fixture_reservation.reserved_total,
    snapshot.version = snapshot.version + 1,
    snapshot.updated_at = CURRENT_TIMESTAMP(3);

DROP TEMPORARY TABLE k6_sequence;
