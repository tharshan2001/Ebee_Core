#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$DIR/.env.db"
BACKUPS_DIR="$DIR/backups"

# --- Load config ---
if [ -f "$ENV_FILE" ]; then
  set -a
  . "$ENV_FILE"
  set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-ebee_db}"
DB_USER="${DB_USER:-antonabitharshan}"

CONN="--host=$DB_HOST --port=$DB_PORT --username=$DB_USER"
export PGPASSWORD="${DB_PASSWORD:-}"

# --- Helpers ---
timestamp() { date +%Y%m%d_%H%M%S; }

require_file() {
  if [ ! -f "$1" ]; then echo "Error: file not found: $1" >&2; exit 1; fi
  if [ ! -s "$1" ]; then echo "Error: file is empty: $1" >&2; exit 1; fi
}

can_connect() {
  psql $CONN -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1
}

verify_dump() {
  if pg_restore -l "$1" >/dev/null 2>&1; then
    return 0
  else
    return 1
  fi
}

# --- Commands ---

cmd_backup() {
  mkdir -p "$BACKUPS_DIR"
  local file="$BACKUPS_DIR/${DB_NAME}_$(timestamp).dump"
  echo "Backing up $DB_NAME → $(basename "$file")"
  pg_dump $CONN -d "$DB_NAME" -Fc --verbose --no-owner > "$file"
  if verify_dump "$file"; then
    local size
    size=$(du -h "$file" | cut -f1)
    echo "✓ Verified: $file ($size)"
  else
    rm -f "$file"
    echo "✗ Backup corrupt — deleted. Aborting." >&2
    exit 1
  fi
}

cmd_restore() {
  local dump_file="$1"
  require_file "$dump_file"
  if ! verify_dump "$dump_file"; then
    echo "✗ Dump file is invalid or corrupt: $dump_file" >&2
    exit 1
  fi

  # Safety: auto-backup first
  echo "→ Auto-backup before restore..."
  cmd_backup

  echo "→ Dropping and recreating database $DB_NAME..."
  psql $CONN -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" >/dev/null 2>&1
  createdb $CONN "$DB_NAME"

  echo "→ Restoring from $(basename "$dump_file")..."
  pg_restore $CONN -d "$DB_NAME" --exit-on-error --no-owner "$dump_file"

  echo "✓ Restore complete"
}

cmd_list() {
  if [ ! -d "$BACKUPS_DIR" ] || [ -z "$(ls -A "$BACKUPS_DIR" 2>/dev/null)" ]; then
    echo "No backups found in $BACKUPS_DIR"
    exit 0
  fi
  printf "%-30s %15s  %s\n" "FILE" "SIZE"  "DATE"
  printf "%-30s %15s  %s\n" "----" "----"  "----"
  ls -1tr "$BACKUPS_DIR" | while read -r f; do
    local path="$BACKUPS_DIR/$f"
    local size
    size=$(du -h "$path" | cut -f1)
    local date
    date=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M" "$path" 2>/dev/null || stat -c "%y" "$path" 2>/dev/null | cut -d. -f1)
    printf "%-30s %15s  %s\n" "$f" "$size" "$date"
  done
}

cmd_verify() {
  require_file "$1"
  local file="$1"
  local size
  size=$(du -h "$file" | cut -f1)
  echo "File: $file"
  echo "Size: $size"
  if verify_dump "$file"; then
    echo "Status: ✓ Valid"
    local count
    count=$(pg_restore -l "$file" | grep -c "^[0-9]" || true)
    echo "Objects: $count"
  else
    echo "Status: ✗ Invalid or corrupt"
    exit 1
  fi
}

# --- Dispatch ---
case "${1:-help}" in
  backup)
    if ! can_connect; then echo "✗ Cannot connect to $DB_NAME at $DB_HOST:$DB_PORT" >&2; exit 1; fi
    cmd_backup
    ;;
  restore)
    if [ -z "${2:-}" ]; then echo "Usage: $0 restore <dump-file>" >&2; exit 1; fi
    if ! can_connect; then echo "✗ Cannot connect to $DB_NAME at $DB_HOST:$DB_PORT" >&2; exit 1; fi
    cmd_restore "$2"
    ;;
  list)
    cmd_list
    ;;
  verify)
    if [ -z "${2:-}" ]; then echo "Usage: $0 verify <dump-file>" >&2; exit 1; fi
    cmd_verify "$2"
    ;;
  help|--help|-h)
    echo "Usage: $0 <command> [args]"
    echo ""
    echo "Commands:"
    echo "  backup                Create a timestamped dump of $DB_NAME"
    echo "  restore <file>        Auto-backup DB, then restore from dump file"
    echo "  list                  List available backup files"
    echo "  verify <file>         Verify a dump file is valid"
    echo ""
    echo "Config: $ENV_FILE"
    ;;
  *)
    echo "Unknown command: $1" >&2
    echo "Usage: $0 backup|restore|list|verify" >&2
    exit 1
    ;;
esac
