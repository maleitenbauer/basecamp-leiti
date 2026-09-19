#!/usr/bin/env bash
# Nightly Postgres dump. Cron example (as the deploy user):
#   15 3 * * * /opt/basecamp/repo/scripts/backup.sh >> /opt/basecamp/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/basecamp/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
mkdir -p "$BACKUP_DIR"

file="$BACKUP_DIR/basecamp-$(date +%F-%H%M).sql.gz"
docker compose -p basecamp exec -T postgres pg_dump -U "${POSTGRES_USER:-basecamp}" "${POSTGRES_DB:-basecamp}" | gzip > "$file"
find "$BACKUP_DIR" -name 'basecamp-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) wrote $file"
