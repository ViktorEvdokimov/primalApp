#!/usr/bin/env bash
# Выгрузка «Инфо» (статьи и картинки) из базы в файл по умолчанию для развёртывания (doc/setup.md §3.14, qa № 145):
#   bash deploy/export-info.sh                  → backend/src/main/resources/info/default-info.json
#   bash deploy/export-info.sh <файл>           → в указанный файл
# Сервер при запуске заполняет пустое «Инфо» из этого файла (InfoSeeder); формат — как у «Выгрузить» в
# администрировании, его же принимает «Заменить всё из выгрузки». Работает с docker-compose.yml рядом с deploy.
set -euo pipefail

cd "$(dirname "$0")/.."
target="${1:-backend/src/main/resources/info/default-info.json}"
mkdir -p "$(dirname "$target")"

docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA' > "$target.part" <<'SQL'
select jsonb_pretty(jsonb_build_object(
    'format', 1,
    'entries', coalesce((
        select jsonb_agg(jsonb_build_object(
                   'section', e.section,
                   'title', e.title,
                   'body', e.body,
                   'image', case when i.id is null then null else jsonb_build_object(
                       'contentType', i.content_type,
                       'data', replace(encode(i.data, 'base64'), E'\n', '')) end)
                   order by e.id)
        from info_entry e left join info_image i on i.id = e.image_id), '[]'::jsonb)));
SQL
mv "$target.part" "$target"
count=$(grep -c '"section"' "$target" || true)
echo "«Инфо» выгружено: $target (статей: $count, $(du -h "$target" | cut -f1))"
