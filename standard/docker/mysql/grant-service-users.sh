#!/bin/bash
set -euo pipefail

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS '${WALLET_DB_USERNAME:-wallet}'@'%' IDENTIFIED BY '${WALLET_DB_PASSWORD:-wallet-local}';
    GRANT ALL PRIVILEGES ON trade_walletservice.* TO '${WALLET_DB_USERNAME:-wallet}'@'%';
    GRANT ALL PRIVILEGES ON trade_ledgerservice.* TO '${LEDGER_DB_USERNAME:-ledger}'@'%';
    FLUSH PRIVILEGES;
EOSQL
