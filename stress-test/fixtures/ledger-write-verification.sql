-- The runner sets these session variables before loading this file:
--   @run_prefix, @base_amount, @amount_span
-- The final result line is a numeric failure count consumed by the shell runner.

DROP TEMPORARY TABLE IF EXISTS k6_expected_entries;
CREATE TEMPORARY TABLE k6_expected_entries (
    txn_id varchar(64) NOT NULL,
    asset_id varchar(64) NOT NULL,
    account_id varchar(64) NOT NULL,
    direction tinyint NOT NULL,
    amount bigint NOT NULL,
    KEY (txn_id)
);

INSERT INTO k6_expected_entries (txn_id, asset_id, account_id, direction, amount)
WITH RECURSIVE
pair_number AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM pair_number WHERE n < 9
),
run_transaction AS (
    SELECT
        lt.txn_id,
        CAST(SUBSTRING_INDEX(lt.reference_id, ':', -1) AS UNSIGNED) AS sequence_number,
        CASE
            WHEN MOD(CAST(SUBSTRING_INDEX(lt.reference_id, ':', -1) AS UNSIGNED), 20) < 9 THEN 2
            WHEN MOD(CAST(SUBSTRING_INDEX(lt.reference_id, ':', -1) AS UNSIGNED), 20) < 18 THEN 4
            ELSE 20
        END AS expected_entry_count
    FROM ledger_transactions lt
    WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix
),
expected_pair AS (
    SELECT
        rt.txn_id,
        rt.sequence_number,
        pn.n AS pair_index,
        MOD(rt.sequence_number, 10) AS asset_index,
        MOD(rt.sequence_number + pn.n * 2, 10) AS debit_offset,
        MOD(rt.sequence_number + pn.n * 2 + 1, 10) AS credit_offset,
        @base_amount + MOD(rt.sequence_number + pn.n, @amount_span) AS amount
    FROM run_transaction rt
    JOIN pair_number pn ON pn.n < rt.expected_entry_count / 2
)
SELECT
    ep.txn_id,
    CONCAT('k6-write-asset-', LPAD(ep.asset_index, 2, '0')),
    CONCAT('k6-write-account-id-', LPAD(ep.asset_index * 10 + ep.debit_offset, 3, '0')),
    2,
    ep.amount
FROM expected_pair ep
UNION ALL
SELECT
    ep.txn_id,
    CONCAT('k6-write-asset-', LPAD(ep.asset_index, 2, '0')),
    CONCAT('k6-write-account-id-', LPAD(ep.asset_index * 10 + ep.credit_offset, 3, '0')),
    1,
    ep.amount
FROM expected_pair ep;

SELECT CONCAT(
    'persisted transactions=',
    COUNT(*)
)
FROM ledger_transactions lt
WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix;

SELECT CONCAT(
    'persisted entries=',
    COUNT(*)
)
FROM ledger_entries le
JOIN ledger_transactions lt ON lt.txn_id = le.txn_id
WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix;

DROP TEMPORARY TABLE IF EXISTS k6_verification_failures;
CREATE TEMPORARY TABLE k6_verification_failures (failure_type varchar(64));

INSERT INTO k6_verification_failures (failure_type)
SELECT 'unbalanced_transaction_asset'
FROM ledger_entries le
JOIN ledger_transactions lt ON lt.txn_id = le.txn_id
WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix
GROUP BY le.txn_id, le.asset_id
HAVING
    SUM(CASE WHEN le.direction = 1 THEN le.amount ELSE 0 END)
    <>
    SUM(CASE WHEN le.direction = 2 THEN le.amount ELSE 0 END);

INSERT INTO k6_verification_failures (failure_type)
SELECT 'missing_or_different_expected_entry'
FROM (
    SELECT txn_id, asset_id, account_id, direction, amount, COUNT(*) AS row_count
    FROM k6_expected_entries
    GROUP BY txn_id, asset_id, account_id, direction, amount
) expected
LEFT JOIN (
    SELECT le.txn_id, le.asset_id, le.account_id, le.direction, le.amount, COUNT(*) AS row_count
    FROM ledger_entries le
    JOIN ledger_transactions lt ON lt.txn_id = le.txn_id
    WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix
    GROUP BY le.txn_id, le.asset_id, le.account_id, le.direction, le.amount
) actual
    ON actual.txn_id = expected.txn_id
    AND actual.asset_id = expected.asset_id
    AND actual.account_id = expected.account_id
    AND actual.direction = expected.direction
    AND actual.amount = expected.amount
WHERE actual.row_count IS NULL OR actual.row_count <> expected.row_count;

INSERT INTO k6_verification_failures (failure_type)
SELECT 'unexpected_persisted_entry'
FROM (
    SELECT le.txn_id, le.asset_id, le.account_id, le.direction, le.amount, COUNT(*) AS row_count
    FROM ledger_entries le
    JOIN ledger_transactions lt ON lt.txn_id = le.txn_id
    WHERE LEFT(lt.reference_id, CHAR_LENGTH(@run_prefix)) = @run_prefix
    GROUP BY le.txn_id, le.asset_id, le.account_id, le.direction, le.amount
) actual
LEFT JOIN (
    SELECT txn_id, asset_id, account_id, direction, amount, COUNT(*) AS row_count
    FROM k6_expected_entries
    GROUP BY txn_id, asset_id, account_id, direction, amount
) expected
    ON expected.txn_id = actual.txn_id
    AND expected.asset_id = actual.asset_id
    AND expected.account_id = actual.account_id
    AND expected.direction = actual.direction
    AND expected.amount = actual.amount
WHERE expected.row_count IS NULL OR expected.row_count <> actual.row_count;

SELECT CONCAT(failure_type, '=', COUNT(*))
FROM k6_verification_failures
GROUP BY failure_type
ORDER BY failure_type;

-- Keep this numeric count as the final output line for run-write-stress.sh.
SELECT COUNT(*) FROM k6_verification_failures;
