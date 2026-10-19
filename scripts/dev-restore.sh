#!/usr/bin/env bash
# Restore an anonymized data-only export into TARGET_DB, once.
#
# The restore is recorded in TARGET_DB itself (table _restore.log), in the same
# transaction as the data. If a restore is already recorded, the script exits 0
# without touching anything, so it is safe to run on every `docker compose up`.
#
# The record lives in the database volume: wipe the volume and the next run
# restores again. To force a re-restore on an existing database:
#   DROP SCHEMA _restore CASCADE;   (and empty the tables first)
#
# The schema must already exist in TARGET_DB (the dump is data only).
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
export PGHOST PGUSER PGPASSWORD

TARGET_DB="${TARGET_DB:-ramitos}"
DUMP_DIR="${DUMP_DIR:-/exports}"
# Default: the newest .dump in DUMP_DIR
DUMP_FILE="${DUMP_FILE:-$(ls -1t "${DUMP_DIR}"/*.dump 2>/dev/null | head -n1 || true)}"

PSQL=(psql -X -q -v ON_ERROR_STOP=1 -d "${TARGET_DB}")

already=""
if [[ "$("${PSQL[@]}" -At -c "SELECT to_regclass('_restore.log') IS NOT NULL")" == "t" ]]; then
    already=$("${PSQL[@]}" -At -c "
        SELECT format('%s at %s', file, restored_at)
        FROM _restore.log ORDER BY restored_at DESC LIMIT 1")
fi
if [[ -n "${already}" ]]; then
    echo "==> ${TARGET_DB} already restored (${already}); skipping"
    exit 0
fi

if [[ -z "${DUMP_FILE}" || ! -f "${DUMP_FILE}" ]]; then
    echo "!! No dump file found (DUMP_FILE='${DUMP_FILE}', DUMP_DIR='${DUMP_DIR}')" >&2
    exit 1
fi

if [[ "$("${PSQL[@]}" -At -c "SELECT to_regclass('accounts') IS NOT NULL")" != "t" ]]; then
    echo "!! ${TARGET_DB} has no schema yet (table accounts not found); run migrations first" >&2
    exit 1
fi

echo "==> Restoring $(basename "${DUMP_FILE}") into ${TARGET_DB}"
# pg_restore emits SQL; psql applies it plus the marker in ONE transaction,
# so either everything (data + marker) is committed or nothing is.
{
    echo "CREATE SCHEMA IF NOT EXISTS _restore;"
    echo "CREATE TABLE IF NOT EXISTS _restore.log (file text NOT NULL, restored_at timestamptz NOT NULL DEFAULT now());"
    pg_restore --data-only --disable-triggers --no-owner -f - "${DUMP_FILE}"
    printf "INSERT INTO _restore.log (file) VALUES (%s);\n" \
        "$(printf '%s' "$(basename "${DUMP_FILE}")" | sed "s/'/''/g; s/^/'/; s/$/'/")"
} | "${PSQL[@]}" --single-transaction > /dev/null

echo "==> Done"
