-- The runner selects the wallet schema explicitly. This file is destructive
-- and is executed only when WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN=true.
TRUNCATE TABLE wallet_actions;
TRUNCATE TABLE wallet_reservations;
TRUNCATE TABLE wallet_transactions;
TRUNCATE TABLE outbox;
TRUNCATE TABLE wallet_account_mappings;
TRUNCATE TABLE balance_snapshots;
TRUNCATE TABLE wallets;
