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

WALLET_STRESS_RUN_ID="${WALLET_STRESS_RUN_ID:-$(date -u +%m%d%H%M%S)}"
WALLET_STRESS_PROFILE="${WALLET_STRESS_PROFILE:-discovery}"
WALLET_WRITE_OPERATION="${WALLET_WRITE_OPERATION:-atomic}"
WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN="${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN:-false}"
WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN="${WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN:-false}"
WALLET_STRESS_WALLET_COUNT="${WALLET_STRESS_WALLET_COUNT:-100}"
WALLET_STRESS_BASE_AMOUNT="${WALLET_STRESS_BASE_AMOUNT:-100}"
WALLET_STRESS_AMOUNT_SPAN="${WALLET_STRESS_AMOUNT_SPAN:-900}"
WALLET_STRESS_BALANCE_SAFETY_PERCENT="${WALLET_STRESS_BALANCE_SAFETY_PERCENT:-120}"
WALLET_STRESS_STAGE_SECONDS="${WALLET_STRESS_STAGE_SECONDS:-30}"
WALLET_STRESS_WARM_RATE="${WALLET_STRESS_WARM_RATE:-5}"
WALLET_STRESS_DISCOVERY_RATE_STEP="${WALLET_STRESS_DISCOVERY_RATE_STEP:-10}"
WALLET_STRESS_DISCOVERY_STEPS="${WALLET_STRESS_DISCOVERY_STEPS:-8}"
WALLET_STRESS_SUSTAINABLE_RATE="${WALLET_STRESS_SUSTAINABLE_RATE:-50}"
WALLET_STRESS_SOAK_SECONDS="${WALLET_STRESS_SOAK_SECONDS:-1800}"
WALLET_STRESS_MAX_VUS="${WALLET_STRESS_MAX_VUS:-500}"
WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS="${WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS:-300}"
WALLET_STRESS_SETTLEMENT_POLL_SECONDS="${WALLET_STRESS_SETTLEMENT_POLL_SECONDS:-2}"
WALLET_STRESS_SETTLEMENT_QUIET_SECONDS="${WALLET_STRESS_SETTLEMENT_QUIET_SECONDS:-10}"

require_positive_integer() {
  name="$1"
  value="$2"
  case "${value}" in
    ''|*[!0-9]*)
      echo "${name} must be a positive integer" >&2
      exit 1
      ;;
  esac
  if [ "${value}" -eq 0 ]; then
    echo "${name} must be a positive integer" >&2
    exit 1
  fi
}

case "${WALLET_STRESS_RUN_ID}" in
  ''|*[!A-Za-z0-9-]*)
    echo "WALLET_STRESS_RUN_ID must contain only letters, digits, and hyphens" >&2
    exit 1
    ;;
esac
if [ "${#WALLET_STRESS_RUN_ID}" -gt 12 ]; then
  echo "WALLET_STRESS_RUN_ID must not exceed 12 characters" >&2
  exit 1
fi
case "${WALLET_STRESS_PROFILE}" in
  discovery|soak) ;;
  *) echo "WALLET_STRESS_PROFILE must be discovery or soak" >&2; exit 1 ;;
esac
case "${WALLET_WRITE_OPERATION}" in
  atomic|reserve|apply-release|apply-earmark) ;;
  *)
    echo "WALLET_WRITE_OPERATION must be atomic, reserve, apply-release, or apply-earmark" >&2
    exit 1
    ;;
esac
case "${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN}" in
  true|false) ;;
  *) echo "WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN must be true or false" >&2; exit 1 ;;
esac
case "${WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN}" in
  true|false) ;;
  *) echo "WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN must be true or false" >&2; exit 1 ;;
esac

for pair in \
  "WALLET_STRESS_WALLET_COUNT:${WALLET_STRESS_WALLET_COUNT}" \
  "WALLET_STRESS_BASE_AMOUNT:${WALLET_STRESS_BASE_AMOUNT}" \
  "WALLET_STRESS_AMOUNT_SPAN:${WALLET_STRESS_AMOUNT_SPAN}" \
  "WALLET_STRESS_BALANCE_SAFETY_PERCENT:${WALLET_STRESS_BALANCE_SAFETY_PERCENT}" \
  "WALLET_STRESS_STAGE_SECONDS:${WALLET_STRESS_STAGE_SECONDS}" \
  "WALLET_STRESS_WARM_RATE:${WALLET_STRESS_WARM_RATE}" \
  "WALLET_STRESS_DISCOVERY_RATE_STEP:${WALLET_STRESS_DISCOVERY_RATE_STEP}" \
  "WALLET_STRESS_DISCOVERY_STEPS:${WALLET_STRESS_DISCOVERY_STEPS}" \
  "WALLET_STRESS_SUSTAINABLE_RATE:${WALLET_STRESS_SUSTAINABLE_RATE}" \
  "WALLET_STRESS_SOAK_SECONDS:${WALLET_STRESS_SOAK_SECONDS}" \
  "WALLET_STRESS_MAX_VUS:${WALLET_STRESS_MAX_VUS}" \
  "WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS:${WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS}" \
  "WALLET_STRESS_SETTLEMENT_POLL_SECONDS:${WALLET_STRESS_SETTLEMENT_POLL_SECONDS}" \
  "WALLET_STRESS_SETTLEMENT_QUIET_SECONDS:${WALLET_STRESS_SETTLEMENT_QUIET_SECONDS}"; do
  require_positive_integer "${pair%%:*}" "${pair#*:}"
done

if [ "${WALLET_STRESS_BALANCE_SAFETY_PERCENT}" -lt 100 ]; then
  echo "WALLET_STRESS_BALANCE_SAFETY_PERCENT must be >= 100" >&2
  exit 1
fi
if [ "${WALLET_STRESS_WALLET_COUNT}" -gt 5000000 ]; then
  echo "WALLET_STRESS_WALLET_COUNT must not exceed 5000000" >&2
  exit 1
fi
if { [ "${WALLET_WRITE_OPERATION}" = "atomic" ] \
    || [ "${WALLET_WRITE_OPERATION}" = "apply-earmark" ]; } \
    && [ "${WALLET_STRESS_WALLET_COUNT}" -lt 2 ]; then
  echo "${WALLET_WRITE_OPERATION} requires at least two wallets" >&2
  exit 1
fi

# Compute a conservative upper bound because apply fixtures and reserve
# headroom must exist before an open-model test begins. The maximum target rate
# is applied to every second, including ramps and drain, then VU margin is added.
if [ "${WALLET_STRESS_PROFILE}" = "discovery" ]; then
  max_rate=$((WALLET_STRESS_WARM_RATE + WALLET_STRESS_DISCOVERY_RATE_STEP * WALLET_STRESS_DISCOVERY_STEPS))
  profile_seconds=$((WALLET_STRESS_STAGE_SECONDS * (WALLET_STRESS_DISCOVERY_STEPS + 2)))
else
  max_rate=$(((WALLET_STRESS_SUSTAINABLE_RATE * 80 + 50) / 100))
  if [ "${max_rate}" -lt "${WALLET_STRESS_WARM_RATE}" ]; then
    max_rate="${WALLET_STRESS_WARM_RATE}"
  fi
  profile_seconds=$((WALLET_STRESS_STAGE_SECONDS * 3 + WALLET_STRESS_SOAK_SECONDS))
fi
calculated_requests=$(((max_rate * profile_seconds * 120 + 99) / 100 + WALLET_STRESS_MAX_VUS))
WALLET_STRESS_FIXTURE_REQUESTS="${WALLET_STRESS_FIXTURE_REQUESTS:-${calculated_requests}}"
require_positive_integer "WALLET_STRESS_FIXTURE_REQUESTS" "${WALLET_STRESS_FIXTURE_REQUESTS}"
if [ "${WALLET_STRESS_FIXTURE_REQUESTS}" -gt 5000000 ]; then
  echo "WALLET_STRESS_FIXTURE_REQUESTS must not exceed 5000000" >&2
  exit 1
fi

max_amount=$((WALLET_STRESS_BASE_AMOUNT + WALLET_STRESS_AMOUNT_SPAN - 1))
requests_per_wallet=$(((WALLET_STRESS_FIXTURE_REQUESTS + WALLET_STRESS_WALLET_COUNT - 1) / WALLET_STRESS_WALLET_COUNT))
calculated_balance=$(((requests_per_wallet * max_amount * WALLET_STRESS_BALANCE_SAFETY_PERCENT + 99) / 100))
WALLET_STRESS_INITIAL_BALANCE="${WALLET_STRESS_INITIAL_BALANCE:-${calculated_balance}}"
require_positive_integer "WALLET_STRESS_INITIAL_BALANCE" "${WALLET_STRESS_INITIAL_BALANCE}"

prepare_reservations=0
case "${WALLET_WRITE_OPERATION}" in
  apply-release|apply-earmark) prepare_reservations=1 ;;
esac

# The helper is always invoked; with the default false value it exits without
# connecting to Redis. This keeps direct runner and Compose behavior identical.
wallet-cache-reset

wallet_mysql() {
  MYSQL_PWD="${WALLET_DB_PASSWORD}" mysql \
    --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${WALLET_DB_USERNAME}" \
    --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
    "${WALLET_DB_NAME}" "$@"
}

ledger_mysql() {
  MYSQL_PWD="${LEDGER_DB_PASSWORD}" mysql \
    --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${LEDGER_DB_USERNAME}" \
    --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" \
    "${LEDGER_DB_NAME}" "$@"
}

echo "Wallet write stress run ID: ${WALLET_STRESS_RUN_ID}"
echo "Waiting for wallet and ledger MySQL schemas at ${DB_HOST}:${DB_PORT}..."
attempt=1
until MYSQL_PWD="${WALLET_DB_PASSWORD}" mysqladmin \
  --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
  --user="${WALLET_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" ping --silent \
  && MYSQL_PWD="${LEDGER_DB_PASSWORD}" mysqladmin \
  --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
  --user="${LEDGER_DB_USERNAME}" \
  --connect-timeout="${DB_CONNECT_TIMEOUT_SECONDS}" ping --silent; do
  if [ "${attempt}" -ge "${DB_READY_ATTEMPTS}" ]; then
    echo "MySQL did not become ready after ${DB_READY_ATTEMPTS} attempts" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 2
done

if [ "${WALLET_STRESS_TRUNCATE_DB_BEFORE_RUN}" = "true" ]; then
  echo "WARNING: truncating wallet and ledger service tables before the stress test..."
  wallet_mysql < /fixtures/truncate_wallet_service.sql
  ledger_mysql < /fixtures/truncate_ledger_service.sql
fi

echo "Provisioning ${WALLET_STRESS_WALLET_COUNT} wallets and ${WALLET_STRESS_FIXTURE_REQUESTS} request slots..."
{
  printf "SET @run_id = '%s';\n" "${WALLET_STRESS_RUN_ID}"
  printf "SET @wallet_count = %s;\n" "${WALLET_STRESS_WALLET_COUNT}"
  printf "SET @fixture_requests = %s;\n" "${WALLET_STRESS_FIXTURE_REQUESTS}"
  printf "SET @initial_balance = %s;\n" "${WALLET_STRESS_INITIAL_BALANCE}"
  printf "SET @base_amount = %s;\n" "${WALLET_STRESS_BASE_AMOUNT}"
  printf "SET @amount_span = %s;\n" "${WALLET_STRESS_AMOUNT_SPAN}"
  printf "SET @prepare_reservations = %s;\n" "${prepare_reservations}"
  cat /fixtures/wallet-write-fixture.sql
} | wallet_mysql

{
  printf "SET @run_id = '%s';\n" "${WALLET_STRESS_RUN_ID}"
  printf "SET @wallet_count = %s;\n" "${WALLET_STRESS_WALLET_COUNT}"
  cat /fixtures/wallet-write-ledger-fixture.sql
} | ledger_mysql

wallet_fixture_counts="$(wallet_mysql --batch --skip-column-names -e "
  SELECT
    (SELECT COUNT(*) FROM wallets WHERE wallet_id LIKE 'ww:${WALLET_STRESS_RUN_ID}:%'),
    (SELECT COUNT(*) FROM balance_snapshots WHERE wallet_id LIKE 'ww:${WALLET_STRESS_RUN_ID}:%'),
    (SELECT COUNT(*) FROM wallet_account_mappings WHERE wallet_id LIKE 'ww:${WALLET_STRESS_RUN_ID}:%'),
    (SELECT COUNT(*) FROM wallet_reservations WHERE reference_id LIKE 'rv:${WALLET_STRESS_RUN_ID}:%'),
    (SELECT MIN(available) FROM balance_snapshots WHERE wallet_id LIKE 'ww:${WALLET_STRESS_RUN_ID}:%');
")"
set -- ${wallet_fixture_counts}
expected_reservations=0
if [ "${prepare_reservations}" -eq 1 ]; then
  expected_reservations="${WALLET_STRESS_FIXTURE_REQUESTS}"
fi
if [ "${1:-0}" -ne "${WALLET_STRESS_WALLET_COUNT}" ] \
  || [ "${2:-0}" -ne "${WALLET_STRESS_WALLET_COUNT}" ] \
  || [ "${3:-0}" -ne "${WALLET_STRESS_WALLET_COUNT}" ] \
  || [ "${4:-0}" -ne "${expected_reservations}" ] \
  || [ "${5:--1}" -lt 0 ]; then
  echo "Wallet fixture verification failed: wallets=${1:-0}, snapshots=${2:-0}, mappings=${3:-0}, reservations=${4:-0}, min_available=${5:-missing}" >&2
  exit 1
fi
ledger_account_count="$(ledger_mysql --batch --skip-column-names -e "
  SELECT COUNT(*) FROM accounts WHERE reference_id LIKE 'ar:${WALLET_STRESS_RUN_ID}:%';
")"
if [ "${ledger_account_count}" -ne "${WALLET_STRESS_WALLET_COUNT}" ]; then
  echo "Ledger fixture verification failed: accounts=${ledger_account_count}" >&2
  exit 1
fi

export WALLET_STRESS_RUN_ID WALLET_STRESS_PROFILE WALLET_WRITE_OPERATION
export WALLET_STRESS_FIXTURE_REQUESTS WALLET_STRESS_INITIAL_BALANCE

echo "Fixtures ready; starting ${WALLET_STRESS_PROFILE} for ${WALLET_WRITE_OPERATION}..."
set +e
k6 run /scripts/wallet-grpc-write-stress.js
k6_status=$?
set -e

case "${WALLET_WRITE_OPERATION}" in
  atomic) operation_code=a ;;
  reserve) operation_code=r ;;
  apply-release) operation_code=l ;;
  apply-earmark) operation_code=e ;;
esac
run_prefix="wt:${WALLET_STRESS_RUN_ID}:${operation_code}:"

# Atomic and earmark gRPC replies only acknowledge acceptance. Poll the
# authoritative wallet DB until both transaction replies and outbox delivery
# are stable, bounded by an explicit timeout.
settlement_failed=0
if [ "${WALLET_WRITE_OPERATION}" = "atomic" ] \
  || [ "${WALLET_WRITE_OPERATION}" = "apply-earmark" ]; then
  echo "Waiting for ledger-backed wallet transactions to settle..."
  elapsed=0
  quiet_elapsed=0
  while [ "${elapsed}" -lt "${WALLET_STRESS_SETTLEMENT_TIMEOUT_SECONDS}" ]; do
    settlement_counts="$(wallet_mysql --batch --skip-column-names -e "
      SELECT
        COALESCE(SUM(txn.txn_status <> 2), 0),
        COALESCE(SUM(event.id IS NULL OR event.outbox_status <> 2), 0)
      FROM wallet_transactions txn
      LEFT JOIN outbox event ON event.command_id = txn.txn_id
      WHERE txn.reference_id LIKE '${run_prefix}%';
    ")"
    set -- ${settlement_counts}
    pending_transactions="${1:-0}"
    unsettled_outboxes="${2:-0}"
    if [ "${pending_transactions}" -eq 0 ] && [ "${unsettled_outboxes}" -eq 0 ]; then
      quiet_elapsed=$((quiet_elapsed + WALLET_STRESS_SETTLEMENT_POLL_SECONDS))
      if [ "${quiet_elapsed}" -ge "${WALLET_STRESS_SETTLEMENT_QUIET_SECONDS}" ]; then
        break
      fi
    else
      quiet_elapsed=0
    fi
    sleep "${WALLET_STRESS_SETTLEMENT_POLL_SECONDS}"
    elapsed=$((elapsed + WALLET_STRESS_SETTLEMENT_POLL_SECONDS))
  done
  if [ "${quiet_elapsed}" -lt "${WALLET_STRESS_SETTLEMENT_QUIET_SECONDS}" ]; then
    echo "Settlement timed out: pending_transactions=${pending_transactions:-unknown}, unsettled_outboxes=${unsettled_outboxes:-unknown}" >&2
    settlement_failed=1
  fi
fi

echo "Reconciling wallet state and durable effects..."
wallet_verification_output="$({
  printf "SET @run_prefix = '%s';\n" "${run_prefix}"
  printf "SET @run_id = '%s';\n" "${WALLET_STRESS_RUN_ID}"
  printf "SET @operation = '%s';\n" "${WALLET_WRITE_OPERATION}"
  printf "SET @base_amount = %s;\n" "${WALLET_STRESS_BASE_AMOUNT}"
  printf "SET @amount_span = %s;\n" "${WALLET_STRESS_AMOUNT_SPAN}"
  printf "SET @wallet_count = %s;\n" "${WALLET_STRESS_WALLET_COUNT}"
  printf "SET @initial_balance = %s;\n" "${WALLET_STRESS_INITIAL_BALANCE}"
  cat /fixtures/wallet-write-verification.sql
} | wallet_mysql --batch --skip-column-names)"
printf '%s\n' "${wallet_verification_output}"
wallet_failures="$(printf '%s\n' "${wallet_verification_output}" | tail -n 1)"

ledger_failures=0
if [ "${WALLET_WRITE_OPERATION}" = "atomic" ] \
  || [ "${WALLET_WRITE_OPERATION}" = "apply-earmark" ]; then
  wallet_txn_ids="$(wallet_mysql --batch --skip-column-names -e "
    SELECT txn_id FROM wallet_transactions
    WHERE reference_id LIKE '${run_prefix}%';
  ")"
  ledger_verification_output="$({
    printf '%s\n' 'CREATE TEMPORARY TABLE k6_expected_wallet_txns (txn_id varchar(64) NOT NULL PRIMARY KEY);'
    printf '%s\n' "${wallet_txn_ids}" | awk 'NF { printf "INSERT INTO k6_expected_wallet_txns VALUES (\047%s\047);\n", $0 }'
    cat /fixtures/wallet-write-ledger-verification.sql
  } | ledger_mysql --batch --skip-column-names)"
  printf '%s\n' "${ledger_verification_output}"
  ledger_failures="$(printf '%s\n' "${ledger_verification_output}" | tail -n 1)"
fi

if [ "${settlement_failed}" -ne 0 ] \
  || [ "${wallet_failures}" != "0" ] \
  || [ "${ledger_failures}" != "0" ]; then
  echo "Wallet stress reconciliation failed" >&2
  exit 1
fi
if [ "${k6_status}" -ne 0 ]; then
  echo "k6 exited with status ${k6_status}" >&2
  exit "${k6_status}"
fi

echo "Wallet write stress run completed successfully."
