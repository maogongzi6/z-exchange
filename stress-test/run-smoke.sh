#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-172.31.18.211}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-trade_ledgerservice}"
DB_USERNAME="${DB_USERNAME:-ledger}"
DB_PASSWORD="${DB_PASSWORD:-ledger-local}"
DB_READY_ATTEMPTS="${DB_READY_ATTEMPTS:-30}"

# MYSQL_PWD avoids placing the password in command arguments and therefore in
# process listings. The container is short-lived and receives the same database
# secret that ledger-service uses.
export MYSQL_PWD="${DB_PASSWORD}"

attempt=1
until mysqladmin \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  ping --silent; do
  if [ "${attempt}" -ge "${DB_READY_ATTEMPTS}" ]; then
    echo "MySQL did not become ready after ${DB_READY_ATTEMPTS} attempts" >&2
    exit 1
  fi
  echo "Waiting for MySQL fixture connection (${attempt}/${DB_READY_ATTEMPTS})..."
  attempt=$((attempt + 1))
  sleep 2
done

# Fixture creation is outside k6, so database setup time is not included in any
# gRPC request latency or throughput metric.
mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  "${DB_NAME}" < /fixtures/ledger-smoke-fixture.sql

exec k6 run /scripts/ledger-grpc-smoke.js
