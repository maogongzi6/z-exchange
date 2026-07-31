#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-172.31.18.211}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-trade_ledgerservice}"
DB_USERNAME="${DB_USERNAME:-ledger}"
DB_PASSWORD="${DB_PASSWORD:-ledger-local}"
DB_READY_ATTEMPTS="${DB_READY_ATTEMPTS:-30}"
DB_CONNECT_TIMEOUT_SECONDS="${DB_CONNECT_TIMEOUT_SECONDS:-5}"
LEDGER_STRESS_RUN_ID="${LEDGER_STRESS_RUN_ID:-$(date -u +%Y%m%d%H%M%S)}"
LEDGER_STRESS_BASE_AMOUNT="${LEDGER_STRESS_BASE_AMOUNT:-100}"
LEDGER_STRESS_AMOUNT_SPAN="${LEDGER_STRESS_AMOUNT_SPAN:-900}"

case "${LEDGER_STRESS_RUN_ID}" in
  *[!A-Za-z0-9-]*|'')
    echo "LEDGER_STRESS_RUN_ID must contain only letters, digits, or hyphens" >&2
    exit 1
    ;;
esac
if [ "${#LEDGER_STRESS_RUN_ID}" -gt 24 ]; then
  echo "LEDGER_STRESS_RUN_ID must not exceed 24 characters" >&2
  exit 1
fi
case "${LEDGER_STRESS_BASE_AMOUNT}" in
  *[!0-9]*|'')
    echo "LEDGER_STRESS_BASE_AMOUNT and LEDGER_STRESS_AMOUNT_SPAN must be positive integers" >&2
    exit 1
    ;;
esac
case "${LEDGER_STRESS_AMOUNT_SPAN}" in
  *[!0-9]*|'')
    echo "LEDGER_STRESS_BASE_AMOUNT and LEDGER_STRESS_AMOUNT_SPAN must be positive integers" >&2
    exit 1
    ;;
esac
if [ "${LEDGER_STRESS_BASE_AMOUNT}" -eq 0 ] || [ "${LEDGER_STRESS_AMOUNT_SPAN}" -eq 0 ]; then
  echo "LEDGER_STRESS_BASE_AMOUNT and LEDGER_STRESS_AMOUNT_SPAN must be positive integers" >&2
  exit 1
fi

export LEDGER_STRESS_RUN_ID
export MYSQL_PWD="${DB_PASSWORD}"

echo "Ledger write stress run ID: ${LEDGER_STRESS_RUN_ID}"
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

echo "MySQL is ready; applying the distributed ledger write fixture..."
mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
  "${DB_NAME}" < /fixtures/ledger-write-fixture.sql

echo "Fixture applied; starting the ${LEDGER_STRESS_PROFILE:-discovery} profile..."
set +e
k6 run /scripts/ledger-grpc-write-stress.js
k6_status=$?
set -e

# The workload is deterministic, so SQL can reconstruct every expected entry
# for committed references without writing a high-volume manifest during load.
echo "Reconciling committed ledger rows for run ${LEDGER_STRESS_RUN_ID}..."
verification_output="$({
  printf "SET @run_prefix = 'k6-write:%s:';\n" "${LEDGER_STRESS_RUN_ID}"
  printf "SET @base_amount = %s;\n" "${LEDGER_STRESS_BASE_AMOUNT}"
  printf "SET @amount_span = %s;\n" "${LEDGER_STRESS_AMOUNT_SPAN}"
  cat /fixtures/ledger-write-verification.sql
} | mysql \
  --batch \
  --skip-column-names \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
  "${DB_NAME}")"
printf '%s\n' "${verification_output}"
verification_failures="$(printf '%s\n' "${verification_output}" | tail -n 1)"

if [ "${verification_failures}" != "0" ]; then
  echo "Ledger reconciliation failed with ${verification_failures} mismatches" >&2
  exit 1
fi
if [ "${k6_status}" -ne 0 ]; then
  echo "k6 exited with status ${k6_status}" >&2
  exit "${k6_status}"
fi

echo "Ledger write stress run and reconciliation completed successfully."
