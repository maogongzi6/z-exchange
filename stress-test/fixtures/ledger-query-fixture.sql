-- Create deterministic immutable transactions for query-only stress tests.
-- @query_fixture_size is set by run-query-stress.sh and is limited to 5000000.
DROP TEMPORARY TABLE IF EXISTS k6_query_sequence;
CREATE TEMPORARY TABLE k6_query_sequence (
    sequence_number int not null primary key
) ENGINE=InnoDB;

INSERT INTO k6_query_sequence (sequence_number)
SELECT
    millions.n * 1000000
        + hundred_thousands.n * 100000
        + ten_thousands.n * 10000
        + thousands.n * 1000
        + hundreds.n * 100
        + tens.n * 10
        + ones.n
FROM (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) millions
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) hundred_thousands
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ten_thousands
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) thousands
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) hundreds
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
WHERE millions.n * 1000000
        + hundred_thousands.n * 100000
        + ten_thousands.n * 10000
        + thousands.n * 1000
        + hundreds.n * 100
        + tens.n * 10
        + ones.n < @query_fixture_size;

INSERT IGNORE INTO ledger_transactions (
    txn_id,
    reference_id,
    metadata,
    version,
    created_at,
    updated_at
)
SELECT
    CONCAT('k6-query-txn-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0')),
    CONCAT('k6-query-ref-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0')),
    JSON_OBJECT('fixture', 'k6-query'),
    1,
    TIMESTAMP('2026-01-01 00:00:00.000'),
    TIMESTAMP('2026-01-01 00:00:00.000')
FROM k6_query_sequence;

INSERT IGNORE INTO ledger_entries (
    entry_id,
    txn_id,
    asset_id,
    account_id,
    amount,
    direction
)
SELECT
    CONCAT('k6-query-entry-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0'), '-d'),
    CONCAT('k6-query-txn-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0')),
    'k6-write-asset-00',
    'k6-write-account-id-000',
    100,
    1
FROM k6_query_sequence;

-- MySQL cannot read the same temporary table twice within one UNION query.
INSERT IGNORE INTO ledger_entries (
    entry_id,
    txn_id,
    asset_id,
    account_id,
    amount,
    direction
)
SELECT
    CONCAT('k6-query-entry-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0'), '-c'),
    CONCAT('k6-query-txn-', LPAD(sequence_number, GREATEST(6, CHAR_LENGTH(sequence_number)), '0')),
    'k6-write-asset-00',
    'k6-write-account-id-001',
    100,
    2
FROM k6_query_sequence;

DROP TEMPORARY TABLE k6_query_sequence;
