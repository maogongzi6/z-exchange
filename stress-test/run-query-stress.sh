#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-172.31.18.211}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-trade_ledgerservice}"
DB_USERNAME="${DB_USERNAME:-ledger}"
DB_PASSWORD="${DB_PASSWORD:-ledger-local}"
DB_READY_ATTEMPTS="${DB_READY_ATTEMPTS:-30}"
DB_CONNECT_TIMEOUT_SECONDS="${DB_CONNECT_TIMEOUT_SECONDS:-5}"
LEDGER_STRESS_TRUNCATE_DB_BEFORE_RUN="${LEDGER_STRESS_TRUNCATE_DB_BEFORE_RUN:-false}"
LEDGER_QUERY_RUN_ID="${LEDGER_QUERY_RUN_ID:-$(date -u +%Y%m%d%H%M%S)}"
LEDGER_QUERY_FIXTURE_SIZE="${LEDGER_QUERY_FIXTURE_SIZE:-10000}"

case "${LEDGER_STRESS_TRUNCATE_DB_BEFORE_RUN}" in
  true|false) ;;
  *)
    echo "LEDGER_STRESS_TRUNCATE_DB_BEFORE_RUN must be true or false" >&2
    exit 1
    ;;
esac

case "${LEDGER_QUERY_RUN_ID}" in
  *[!A-Za-z0-9-]*|'')
    echo "LEDGER_QUERY_RUN_ID must contain only letters, digits, or hyphens" >&2
    exit 1
    ;;
esac
if [ "${#LEDGER_QUERY_RUN_ID}" -gt 24 ]; then
  echo "LEDGER_QUERY_RUN_ID must not exceed 24 characters" >&2
  exit 1
fi
case "${LEDGER_QUERY_FIXTURE_SIZE}" in
  *[!0-9]*|'')
    echo "LEDGER_QUERY_FIXTURE_SIZE must be an integer from 1 to 100000" >&2
    exit 1
    ;;
esac
if [ "${LEDGER_QUERY_FIXTURE_SIZE}" -lt 1 ] || [ "${LEDGER_QUERY_FIXTURE_SIZE}" -gt 100000 ]; then
  echo "LEDGER_QUERY_FIXTURE_SIZE must be an integer from 1 to 100000" >&2
  exit 1
fi

export LEDGER_QUERY_RUN_ID
export MYSQL_PWD="${DB_PASSWORD}"

echo "Ledger query stress run ID: ${LEDGER_QUERY_RUN_ID}"
echo "Waiting for MySQL fixture database at ${DB_HOST}:${DB_PORT}/${DB_NAME}..."

attempt=1
until mysqladmin \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
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

if [ "${LEDGER_STRESS_TRUNCATE_DB_BEFORE_RUN}" = "true" ]; then
  echo "WARNING: truncating all ledger-service tables in ${DB_NAME} before the stress test..."
  mysql \
    --protocol=TCP \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}" \
    --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
    "${DB_NAME}" < /fixtures/truncate_ledger_service.sql
fi

echo "MySQL is ready; applying ${LEDGER_QUERY_FIXTURE_SIZE} query fixtures..."
mysql_args="--protocol=TCP --host=${DB_HOST} --port=${DB_PORT} --user=${DB_USERNAME} --connect-timeout=${DB_CONNECT_TIMEOUT_SECONDS}"
mysql ${mysql_args} "${DB_NAME}" < /fixtures/ledger-write-fixture.sql
{
  printf 'SET @query_fixture_size = %s;\n' "${LEDGER_QUERY_FIXTURE_SIZE}"
  cat /fixtures/ledger-query-fixture.sql
} | mysql ${mysql_args} "${DB_NAME}"

# Fail before load generation if a partial fixture would turn expected hits into
# misleading not-found or data-integrity results.
fixture_counts="$(mysql --batch --skip-column-names ${mysql_args} "${DB_NAME}" -e "
SELECT
  (SELECT COUNT(*) FROM ledger_transactions
    WHERE txn_id LIKE 'k6-query-txn-%'
      AND CAST(SUBSTRING(txn_id, 14) AS UNSIGNED) < ${LEDGER_QUERY_FIXTURE_SIZE}),
  (SELECT COUNT(*) FROM ledger_entries e
    JOIN ledger_transactions t ON t.txn_id = e.txn_id
    WHERE t.txn_id LIKE 'k6-query-txn-%'
      AND CAST(SUBSTRING(t.txn_id, 14) AS UNSIGNED) < ${LEDGER_QUERY_FIXTURE_SIZE});
")"
set -- ${fixture_counts}
if [ "${1:-0}" -ne "${LEDGER_QUERY_FIXTURE_SIZE}" ] \
    || [ "${2:-0}" -ne "$((LEDGER_QUERY_FIXTURE_SIZE * 2))" ]; then
  echo "Query fixture verification failed: transactions=${1:-0}, entries=${2:-0}" >&2
  exit 1
fi

echo "Fixture verified; starting ${LEDGER_QUERY_PROFILE:-rpc-discovery} for ${LEDGER_QUERY_LOOKUP:-txn-id}..."
exec k6 run /scripts/ledger-grpc-query-stress.js
