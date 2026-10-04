-- Дополнения игрока (qa № 138): по умолчанию у аккаунта все; строка — дополнение, которое игрок убрал в настройках.
-- Хранятся отключённые, а не включённые: новое дополнение в каталоге сразу включено у всех.
create table user_hidden_expansion (
    user_id    bigint      not null references app_user(id) on delete cascade,
    expansion  varchar(16) not null check (expansion in ('NIGHTMARE','FEATHER','POISON','ICE')),
    primary key (user_id, expansion)
);

-- Цены кузни и лаборатории, изменённые администратором (qa № 138), поверх catalog/forge.yaml и lab.yaml.
-- Формат — как в YAML: кузня — [[материи 1-го уровня], [2-го], [3-го]], лаборатория — ["ANY" | "КОД" | "A/B", …].
create table forge_override (
    code        varchar(16) primary key,          -- FIRE_01 … ICE_12
    cost        jsonb       not null,
    updated_at  timestamptz not null,
    updated_by  bigint      references app_user(id) on delete set null
);

-- Характеристики монстров, изменённые администратором (qa № 138), поверх catalog/bosses.yaml: стойки по уровням
-- враждебности, как в YAML — {"0": [{"t": 2, "change": 6}, {"t": null, "change": "ON_DEMAND"}, …], …}.
create table boss_override (
    code        varchar(32) primary key references boss(code),
    stances     jsonb       not null,
    updated_at  timestamptz not null,
    updated_by  bigint      references app_user(id) on delete set null
);

create table lab_override (
    code        varchar(16) primary key,          -- LAB_01 … LAB_06
    cost        jsonb       not null,
    updated_at  timestamptz not null,
    updated_by  bigint      references app_user(id) on delete set null
);
