#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-172.31.18.211}"
DB_PORT="${DB_PORT:-3306}"
WALLET_DB_NAME="${WALLET_DB_NAME:-trade_walletservice}"
WALLET_DB_USERNAME="${WALLET_DB_USERNAME:-wallet}"
WALLET_DB_PASSWORD="${WALLET_DB_PASSWORD:-wallet-local}"
DB_READY_ATTEMPTS="${DB_READY_ATTEMPTS:-30}"
DB_CONNECT_TIMEOUT_SECONDS="${DB_CONNECT_TIMEOUT_SECONDS:-5}"
WALLET_QUERY_RUN_ID="${WALLET_QUERY_RUN_ID:-$(date -u +%m%d%H%M%S)}"
WALLET_QUERY_FIXTURE_SIZE="${WALLET_QUERY_FIXTURE_SIZE:-10000}"
WALLET_QUERY_OWNER_TYPE="${WALLET_QUERY_OWNER_TYPE:-user}"
WALLET_QUERY_SYSTEM_PERCENT="${WALLET_QUERY_SYSTEM_PERCENT:-50}"
WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN="${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN:-false}"
WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN="${WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN:-false}"

case "${WALLET_QUERY_RUN_ID}" in
  ''|*[!A-Za-z0-9-]*)
    echo "WALLET_QUERY_RUN_ID must contain only letters, digits, and hyphens" >&2
    exit 1
    ;;
esac
if [ "${#WALLET_QUERY_RUN_ID}" -gt 12 ]; then
  echo "WALLET_QUERY_RUN_ID must not exceed 12 characters" >&2
  exit 1
fi
case "${WALLET_QUERY_FIXTURE_SIZE}" in
  ''|*[!0-9]*) echo "WALLET_QUERY_FIXTURE_SIZE must be an integer from 1 to 5000000" >&2; exit 1 ;;
esac
if [ "${WALLET_QUERY_FIXTURE_SIZE}" -lt 1 ] \
  || [ "${WALLET_QUERY_FIXTURE_SIZE}" -gt 5000000 ]; then
  echo "WALLET_QUERY_FIXTURE_SIZE must be an integer from 1 to 5000000" >&2
  exit 1
fi
case "${WALLET_QUERY_OWNER_TYPE}" in
  user) fixture_system_percent=0 ;;
  system) fixture_system_percent=100 ;;
  mixed) fixture_system_percent="${WALLET_QUERY_SYSTEM_PERCENT}" ;;
  *) echo "WALLET_QUERY_OWNER_TYPE must be user, system, or mixed" >&2; exit 1 ;;
esac
case "${fixture_system_percent}" in
  ''|*[!0-9]*) echo "WALLET_QUERY_SYSTEM_PERCENT must be an integer from 0 to 100" >&2; exit 1 ;;
esac
if [ "${fixture_system_percent}" -gt 100 ]; then
  echo "WALLET_QUERY_SYSTEM_PERCENT must be an integer from 0 to 100" >&2
  exit 1
fi
case "${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN}" in
  true|false) ;;
  *) echo "WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN must be true or false" >&2; exit 1 ;;
esac
case "${WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN}" in
  true|false) ;;
  *) echo "WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN must be true or false" >&2; exit 1 ;;
esac

wallet-cache-reset

wallet_mysql() {
  MYSQL_PWD="${WALLET_DB_PASSWORD}" mysql \
    --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${WALLET_DB_USERNAME}" \
    --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
    "${WALLET_DB_NAME}" "$@"
}

echo "Wallet query stress run ID: ${WALLET_QUERY_RUN_ID}"
attempt=1
until MYSQL_PWD="${WALLET_DB_PASSWORD}" mysqladmin \
  --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
  --user="${WALLET_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" ping --silent; do
  if [ "${attempt}" -ge "${DB_READY_ATTEMPTS}" ]; then
    echo "MySQL did not become ready after ${DB_READY_ATTEMPTS} attempts" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 2
done

if [ "${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN}" = "true" ]; then
  echo "WARNING: truncating wallet-service tables before the query stress test..."
  wallet_mysql < /fixtures/truncate_wallet_service.sql
fi

echo "Provisioning ${WALLET_QUERY_FIXTURE_SIZE} immutable query wallets..."
{
  printf "SET @run_id = '%s';\n" "${WALLET_QUERY_RUN_ID}"
  printf "SET @fixture_size = %s;\n" "${WALLET_QUERY_FIXTURE_SIZE}"
  printf "SET @system_percent = %s;\n" "${fixture_system_percent}"
  cat /fixtures/wallet-query-fixture.sql
} | wallet_mysql

fixture_counts="$(wallet_mysql --batch --skip-column-names -e "
  SELECT
    (SELECT COUNT(*) FROM wallets WHERE wallet_id LIKE 'qw:${WALLET_QUERY_RUN_ID}:%'),
    (SELECT COUNT(*) FROM balance_snapshots WHERE wallet_id LIKE 'qw:${WALLET_QUERY_RUN_ID}:%');
")"
set -- ${fixture_counts}
if [ "${1:-0}" -ne "${WALLET_QUERY_FIXTURE_SIZE}" ] \
  || [ "${2:-0}" -ne "${WALLET_QUERY_FIXTURE_SIZE}" ]; then
  echo "Query fixture verification failed: wallets=${1:-0}, snapshots=${2:-0}" >&2
  exit 1
fi

export WALLET_QUERY_RUN_ID WALLET_QUERY_FIXTURE_SIZE
echo "Fixture verified; starting ${WALLET_QUERY_PROFILE:-rpc-discovery} for ${WALLET_QUERY_LOOKUP:-wallet-id}..."
exec k6 run /scripts/wallet-grpc-query-stress.js
