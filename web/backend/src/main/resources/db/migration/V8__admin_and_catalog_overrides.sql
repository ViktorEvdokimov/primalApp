-- Администраторы (qa № 137): кто может править награды заданий и глав. Назначаются только вручную скриптом
-- deploy/grant-admin.sh (doc/setup.md §3.13). Первый администратор — аккаунт с логином 89227145790, если он уже есть;
-- иначе — тем же скриптом после регистрации.
create table app_admin (
    user_id     bigint      primary key references app_user(id) on delete cascade,
    granted_at  timestamptz not null default now()
);
insert into app_admin (user_id) select id from app_user where login = '89227145790';

-- Правки наград администратором поверх каталога из YAML: язык эффектов (doc/data-model.md §4.3), как в
-- quest_def/chapter_def. Нет строки — действует YAML; «Вернуть исходные» удаляет строку.
create table quest_override (
    number           smallint    primary key references quest_def(number),
    victory_effects  jsonb       not null,
    expired_effects  jsonb       not null,
    updated_at       timestamptz not null,
    updated_by       bigint      references app_user(id) on delete set null
);

create table chapter_override (
    chapter     smallint    primary key references chapter_def(chapter),
    effects     jsonb       not null,
    updated_at  timestamptz not null,
    updated_by  bigint      references app_user(id) on delete set null
);

-- Новое дополнение «Кошмар» (задания 31–35).
alter table quest_def drop constraint quest_def_expansion_check;
alter table quest_def add constraint quest_def_expansion_check check (expansion in ('NIGHTMARE','FEATHER','POISON','ICE'));
alter table boss drop constraint boss_expansion_check;
alter table boss add constraint boss_expansion_check check (expansion in ('NIGHTMARE','FEATHER','POISON','ICE'));
