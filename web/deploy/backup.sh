#!/usr/bin/env bash
# Резервная копия базы данных сайта (doc/setup.md, раздел 3.10):
#   bash deploy/backup.sh
# Копия — $BACKUP_DIR/primal-ГГГГ-ММ-ДД-ЧЧММ.sql.gz (по умолчанию /opt/primal/backups, каталог создаётся сам);
# копии старше $KEEP_DAYS дней (по умолчанию 14) удаляются. Запускается из любого каталога, работает с
# docker-compose.yml рядом с каталогом deploy. Пароль и имя базы берутся из контейнера postgres.
set -euo pipefail

cd "$(dirname "$0")/.."
BACKUP_DIR="${BACKUP_DIR:-/opt/primal/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
mkdir -p "$BACKUP_DIR"

file="$BACKUP_DIR/primal-$(date +%Y-%m-%d-%H%M).sql.gz"
partial="$file.part"
trap 'rm -f "$partial"' EXIT

# --clean --if-exists: копию можно восстановить поверх существующей базы; --no-owner: владелец — текущий пользователь
docker compose exec -T postgres sh -c 'pg_dump --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > "$partial"
mv "$partial" "$file"

find "$BACKUP_DIR" -maxdepth 1 -name 'primal-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "Резервная копия: $file ($(du -h "$file" | cut -f1))"
