-- The runner creates temporary table k6_expected_wallet_txns(txn_id) first.

DROP TEMPORARY TABLE IF EXISTS k6_wallet_ledger_failures;
CREATE TEMPORARY TABLE k6_wallet_ledger_failures (failure_type varchar(64));

INSERT INTO k6_wallet_ledger_failures
SELECT 'missing_or_duplicate_ledger_transaction'
FROM k6_expected_wallet_txns expected
LEFT JOIN ledger_transactions txn ON txn.reference_id = expected.txn_id
GROUP BY expected.txn_id
HAVING COUNT(txn.id) <> 1;

INSERT INTO k6_wallet_ledger_failures
SELECT 'unexpected_ledger_entry_count'
FROM k6_expected_wallet_txns expected
JOIN ledger_transactions txn ON txn.reference_id = expected.txn_id
LEFT JOIN ledger_entries entry ON entry.txn_id = txn.txn_id
GROUP BY expected.txn_id
HAVING COUNT(entry.id) <> 2;

INSERT INTO k6_wallet_ledger_failures
SELECT 'unbalanced_ledger_transaction'
FROM k6_expected_wallet_txns expected
JOIN ledger_transactions txn ON txn.reference_id = expected.txn_id
JOIN ledger_entries entry ON entry.txn_id = txn.txn_id
GROUP BY expected.txn_id, entry.asset_id
HAVING
    SUM(CASE WHEN entry.direction = 1 THEN entry.amount ELSE 0 END)
    <>
    SUM(CASE WHEN entry.direction = 2 THEN entry.amount ELSE 0 END);

SELECT CONCAT(failure_type, '=', COUNT(*))
FROM k6_wallet_ledger_failures
GROUP BY failure_type
ORDER BY failure_type;

SELECT COUNT(*) FROM k6_wallet_ledger_failures;
