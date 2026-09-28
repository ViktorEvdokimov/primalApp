#!/usr/bin/env bash
# Восстановление базы из резервной копии (doc/setup.md, раздел 3.10):
#   bash deploy/restore.sh /opt/primal/backups/primal-2026-10-01-0330.sql.gz
# Сайт останавливается (backend и web), база пересоздаётся, копия восстанавливается, сайт запускается снова.
# Всё, что изменилось после копии, будет потеряно.
set -euo pipefail

cd "$(dirname "$0")/.."
file="${1:-}"
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "Укажите файл резервной копии: bash deploy/restore.sh /opt/primal/backups/primal-ГГГГ-ММ-ДД-ЧЧММ.sql.gz" >&2
  exit 1
fi
gzip -t "$file"   # повреждённую копию не восстанавливаем — база останется прежней

echo "Останавливаю сайт…"
docker compose stop backend web

echo "Жду базу данных…"
docker compose up -d postgres
for _ in $(seq 1 30); do
  if docker compose exec -T postgres sh -c 'pg_isready -q -U "$POSTGRES_USER" -d postgres'; then break; fi
  sleep 2
done

echo "Пересоздаю базу…"
docker compose exec -T postgres sh -c \
  'dropdb --if-exists --force -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'

echo "Восстанавливаю $file…"
gunzip -c "$file" | docker compose exec -T postgres sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > /dev/null

echo "Запускаю сайт…"
docker compose up -d backend web
echo "Готово: база восстановлена из $file"
