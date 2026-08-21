#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-172.31.18.211}"
DB_PORT="${DB_PORT:-3306}"
WALLET_DB_NAME="${WALLET_DB_NAME:-trade_walletservice}"
WALLET_DB_USERNAME="${WALLET_DB_USERNAME:-wallet}"
WALLET_DB_PASSWORD="${WALLET_DB_PASSWORD:-wallet-local}"
LEDGER_DB_NAME="${LEDGER_DB_NAME:-trade_ledgerservice}"
LEDGER_DB_USERNAME="${LEDGER_DB_USERNAME:-ledger}"
LEDGER_DB_PASSWORD="${LEDGER_DB_PASSWORD:-ledger-local}"
DB_READY_ATTEMPTS="${DB_READY_ATTEMPTS:-30}"
DB_CONNECT_TIMEOUT_SECONDS="${DB_CONNECT_TIMEOUT_SECONDS:-5}"
WALLET_SMOKE_INITIAL_BALANCE="${WALLET_SMOKE_INITIAL_BALANCE:-1000}"
WALLET_SMOKE_RUN_ID="${WALLET_SMOKE_RUN_ID:-$(date +%s)-$$}"

# IDs are interpolated into SQL and database identifiers are limited to 64
# characters. A short restricted suffix keeps fixture SQL safe and predictable.
case "${WALLET_SMOKE_RUN_ID}" in
  ''|*[!A-Za-z0-9-]*)
    echo "WALLET_SMOKE_RUN_ID must contain only letters, digits, and hyphens" >&2
    exit 1
    ;;
esac
if [ "${#WALLET_SMOKE_RUN_ID}" -gt 20 ]; then
  echo "WALLET_SMOKE_RUN_ID must not exceed 20 characters" >&2
  exit 1
fi
case "${WALLET_SMOKE_INITIAL_BALANCE}" in
  ''|*[!0-9]*)
    echo "WALLET_SMOKE_INITIAL_BALANCE must be a non-negative integer" >&2
    exit 1
    ;;
esac

echo "Waiting for MySQL fixture database at ${DB_HOST}:${DB_PORT}..."
attempt=1
until MYSQL_PWD="${WALLET_DB_PASSWORD}" mysqladmin \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${WALLET_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
  ping --silent; do
  if [ "${attempt}" -ge "${DB_READY_ATTEMPTS}" ]; then
    echo "MySQL did not become ready after ${DB_READY_ATTEMPTS} attempts" >&2
    exit 1
  fi
  echo "Waiting for MySQL fixture connection (${attempt}/${DB_READY_ATTEMPTS})..."
  attempt=$((attempt + 1))
  sleep 2
done

echo "Applying ledger fixtures for wallet smoke run ${WALLET_SMOKE_RUN_ID}..."
{
  printf "SET @smoke_run_id = '%s';\n" "${WALLET_SMOKE_RUN_ID}"
  cat /fixtures/wallet-smoke-ledger-fixture.sql
} | MYSQL_PWD="${LEDGER_DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${LEDGER_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
  "${LEDGER_DB_NAME}"

echo "Applying wallet fixtures for wallet smoke run ${WALLET_SMOKE_RUN_ID}..."
{
  printf "SET @smoke_run_id = '%s';\n" "${WALLET_SMOKE_RUN_ID}"
  printf "SET @initial_balance = %s;\n" "${WALLET_SMOKE_INITIAL_BALANCE}"
  cat /fixtures/wallet-smoke-fixture.sql
} | MYSQL_PWD="${WALLET_DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${WALLET_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
  "${WALLET_DB_NAME}"

# Export the generated default so the k6 script derives the exact same IDs.
export WALLET_SMOKE_RUN_ID
echo "Fixtures applied; starting the k6 wallet gRPC smoke test..."
exec k6 run /scripts/wallet-grpc-smoke.js
