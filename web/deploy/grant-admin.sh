#!/usr/bin/env bash
# Назначение администратора (doc/setup.md §3.13, qa № 137) — только этим скриптом, на сайте роль не выдаётся:
#   bash deploy/grant-admin.sh <логин>            назначить (аккаунт с этим логином должен существовать)
#   bash deploy/grant-admin.sh --revoke <логин>   снять роль
#   bash deploy/grant-admin.sh --list             кто администратор
# Работает с docker-compose.yml рядом с каталогом deploy; имя базы и пользователь берутся из контейнера postgres.
set -euo pipefail

cd "$(dirname "$0")/.."

# SQL — со стандартного ввода: так psql подставляет :'login' как строку в кавычках (без SQL-инъекций)
sql() {
  docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA "$@"' psql "$@"
}

case "${1:-}" in
  --list)
    echo "select u.login || ' (с ' || to_char(a.granted_at, 'YYYY-MM-DD') || ')' from app_admin a join app_user u on u.id = a.user_id order by u.login;" | sql
    ;;
  --revoke)
    login="${2:?Укажите логин: bash deploy/grant-admin.sh --revoke <логин>}"
    count=$(echo "with gone as (delete from app_admin where user_id = (select id from app_user where login = lower(:'login')) returning 1) select count(*) from gone;" | sql -v login="$login")
    [ "$count" = "1" ] && echo "Роль администратора снята: $login" || echo "$login не администратор."
    ;;
  ""|-*)
    echo "Использование: bash deploy/grant-admin.sh <логин> | --revoke <логин> | --list" >&2
    exit 2
    ;;
  *)
    login="$1"
    exists=$(echo "select count(*) from app_user where login = lower(:'login');" | sql -v login="$login")
    if [ "$exists" != "1" ]; then
      echo "Аккаунта с логином «$login» нет: сначала зарегистрируйтесь на сайте." >&2
      exit 1
    fi
    echo "insert into app_admin (user_id) select id from app_user where login = lower(:'login') on conflict do nothing;" | sql -v login="$login" >/dev/null
    echo "Администратор: $login. Пункт «Администрирование» появится в главном меню после обновления страницы."
    ;;
esac
