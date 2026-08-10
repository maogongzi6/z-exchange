-- The runner selects DB_NAME explicitly. Do not hard-code a schema here.
TRUNCATE TABLE ledger_entries;
TRUNCATE TABLE ledger_transactions;
TRUNCATE TABLE outbox;
TRUNCATE TABLE accounts;
TRUNCATE TABLE assets;
