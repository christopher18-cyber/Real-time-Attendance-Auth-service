#!/usr/bin/env bash
set -euo pipefail

pg_bin="${SMARTATTEND_PG_BIN:-/opt/homebrew/opt/postgresql@17/bin}"
pg_data="${SMARTATTEND_PG_DATA:-/private/tmp/smartattend-pg17}"
pg_port="${SMARTATTEND_PG_PORT:-55432}"

if [[ ! -x "$pg_bin/initdb" ]]; then
  printf 'PostgreSQL 17 was not found at %s. Install it or set SMARTATTEND_PG_BIN.\n' "$pg_bin" >&2
  exit 1
fi

if [[ ! -f "$pg_data/PG_VERSION" ]]; then
  "$pg_bin/initdb" -D "$pg_data" -U smartattend --auth-local=trust --auth-host=trust --no-instructions
fi

if ! "$pg_bin/pg_ctl" -D "$pg_data" status >/dev/null 2>&1; then
  if "$pg_bin/pg_isready" -h 127.0.0.1 -p "$pg_port" >/dev/null 2>&1; then
    printf 'Port %s is occupied by another PostgreSQL server.\n' "$pg_port" >&2
    exit 1
  fi
  "$pg_bin/pg_ctl" -D "$pg_data" -l "$pg_data/server.log" -o "-h 127.0.0.1 -p $pg_port" start
fi

psql=("$pg_bin/psql" -h 127.0.0.1 -p "$pg_port" -U smartattend -d postgres -At)
if [[ "$("${psql[@]}" -c "select count(*) from pg_database where datname = 'smartattend_test'")" == 0 ]]; then
  "$pg_bin/createdb" -h 127.0.0.1 -p "$pg_port" -U smartattend smartattend_test
fi

printf 'Local PostgreSQL ready: 127.0.0.1:%s/smartattend_test\n' "$pg_port"
