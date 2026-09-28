-- Схема Primal Web, версия 1. Источник — doc/data-model.md §3 (DDL переносится без изменений).
-- Изменения схемы — только новыми миграциями V2__…; каталог заполняет repeatable-миграция R__Catalog (задача 1.7).



create table app_user (
    id            bigint generated always as identity primary key,
    email         varchar(254) not null,                -- нормализованный: trim + lower
    display_name  varchar(60),                          -- null: показывается часть почты до «@»
    created_at    timestamptz  not null default now()
);
create unique index ux_app_user_email on app_user (email);

create table login_challenge (                           -- запрос кода входа
    id             uuid         primary key,
    email          varchar(254) not null,               -- нормализованный
    code_hash      bytea        not null,               -- HMAC-SHA256(pepper, id ‖ code)
    expires_at     timestamptz  not null,               -- создание + 10 минут
    attempts_left  smallint     not null default 5 check (attempts_left >= 0),
    consumed_at    timestamptz,                         -- код введён верно или заменён новым запросом
    requested_ip   inet,
    created_at     timestamptz  not null default now()
);
create index ix_login_challenge_email on login_challenge (email, created_at desc);
-- Фоновая задача удаляет запросы старше суток.

create table device (                                    -- запомненный браузер
    id            uuid         primary key,
    user_id       bigint       references app_user(id) on delete cascade,   -- null: гость по ссылке
    token_hash    bytea        not null unique,          -- SHA-256 токена из cookie PRIMAL_DEVICE
    display_name  varchar(60),                           -- имя гостя; для пользователя — не используется
    user_agent    varchar(300),                          -- для списка устройств: «Chrome, Android»
    created_at    timestamptz  not null default now(),
    last_seen_at  timestamptz  not null default now(),   -- обновляется не чаще раза в час
    revoked_at    timestamptz                            -- «Выйти» / отзыв из списка устройств
);
create index ix_device_user on device (user_id) where revoked_at is null;

create table boss (
    code        varchar(32) primary key,                 -- 'VIRAXEN' (транслитерация)
    name        varchar(60) not null unique,             -- 'Вираксен'
    element     varchar(16) check (element in
                  ('FIRE','HORN','CORAL','CRYSTAL','LIGHTNING','METAL','FEATHER','POISON','ICE')),
                                                         -- null: без стихии (Пробуждённый)
    expansion   varchar(16) check (expansion in ('FEATHER','POISON','ICE')),
    sort_order  smallint    not null                     -- порядок в списке выбора
);

create table boss_stance (
    boss_code             varchar(32) not null references boss(code),
    difficulty            smallint    not null check (difficulty between 0 and 3),
    stance_no             smallint    not null check (stance_no between 1 and 9),
    toughness_per_hunter  smallint    check (toughness_per_hunter > 0),   -- null: нет порога раны (Коровон II)
    change_mode           varchar(12) not null check (change_mode in ('HEALTH','ON_DEMAND','FINAL')),
    change_at_health      smallint    check (change_at_health between 1 and 9),
    primary key (boss_code, difficulty, stance_no),
    check ((change_mode = 'HEALTH') = (change_at_health is not null))
);
-- Доступные сложности босса = distinct difficulty (у Пробуждённого — только 3).

create table achievement_def (
    code  varchar(48)  primary key,          -- 'GOLOS_VOLTYARA'
    name  varchar(100) not null unique       -- 'Голос Волтьяра' (каноническое написание, 42.1)
);

create table quest_def (
    number           smallint     primary key check (number > 0),
    name             varchar(100) not null,               -- 'Память пустыни'
    boss_code        varchar(32)  not null references boss(code),   -- стихия задания = стихия босса
    expansion        varchar(16)  check (expansion in ('FEATHER','POISON','ICE')),
    victory_effects  jsonb        not null default '[]',  -- язык эффектов, §4
    defeat_effects   jsonb        not null default '[]'
);

create table chapter_def (
    chapter    smallint primary key check (chapter between 1 and 11),
    effects    jsonb    not null default '[]',   -- инструкции этапа сюжета главы
    decisions  jsonb    not null default '[]'    -- решения главы: вопрос, варианты → эффекты
);

create table catalog_version (                  -- одна строка: хеш YAML, отдаётся как ETag каталога
    id        boolean primary key default true check (id),
    checksum  varchar(64) not null,
    loaded_at timestamptz not null default now()
);

create table campaign (
    id               bigint generated always as identity primary key,
    owner_id         bigint       not null references app_user(id) on delete cascade,
    name             varchar(100) not null,
    chapter          smallint     not null default 0 check (chapter between 0 and 11),  -- 0 = «Пролог»
    status           varchar(20)  not null default 'ACTIVE'
                       check (status in ('ACTIVE','CHAPTER_TRANSITION','COMPLETED')),
    forge_level      smallint     not null default 1 check (forge_level between 1 and 3),
    lab_level        smallint     not null default 1 check (lab_level between 1 and 3),
    final_boss_code  varchar(32)  references boss(code),  -- задан: следующий бой только с этим боссом (гл. 11)
    progress_seq     integer      not null default 0,     -- растёт при принятой победе и смене главы (§3.4)
    notes            text         not null default '',
    version          integer      not null default 0,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);
create index ix_campaign_owner on campaign (owner_id);
-- Не более 10 кампаний на пользователя — проверка в сервисе (primal.campaign.max-per-user = 10).

create table campaign_hunter (
    id            bigint generated always as identity primary key,
    campaign_id   bigint      not null references campaign(id) on delete cascade,
    hunter_class  varchar(16) not null check (hunter_class in
                    ('DAREON','MIRA','TOREG','LIONAR','KARA','HELEREN','DRUSK','ZARAIA')),
    player_name   varchar(60) not null,
    position      smallint    not null check (position between 1 and 5),   -- порядок в отряде
    unique (campaign_id, hunter_class),
    unique (campaign_id, position)
);
-- В отряде 2–5 охотников: максимум ограничивают CHECK и UNIQUE по position, минимум проверяет сервис
-- (primal.campaign.hunters.min = 2, max = 5; пятый игрок появляется в одном из дополнений).

create table hunter_skill (                             -- строка = ступень открыта
    hunter_id    bigint      not null references campaign_hunter(id) on delete cascade,
    branch       char(1)     not null check (branch in ('A','B','V','G','D')),
    tier         smallint    not null check (tier in (1, 2)),
    unlocked_at  timestamptz not null default now(),
    primary key (hunter_id, branch, tier)
);
-- Ступень 2 требует ступень 1 — проверка в сервисе (SkillValidator из app).

create table hunter_resource (
    hunter_id  bigint      not null references campaign_hunter(id) on delete cascade,
    resource   varchar(16) not null check (resource in (
                 'SCALES','BONES','BLOOD','ZIMIA','IRIDIA','ZLATIA',
                 'NILLEA','TARMARET','ALBALACEA','MELLIS','ANTHEMON','SELICORNIA',
                 'FIRE','HORN','CORAL','CRYSTAL','LIGHTNING','METAL','FEATHER','POISON','ICE')),
    quantity   integer     not null default 0 check (quantity >= 0),
    primary key (hunter_id, resource)
);
-- Начисление атомарно:
--   insert … on conflict (hunter_id, resource) do update set quantity = hunter_resource.quantity + excluded.quantity
-- Уход в минус ловит CHECK → 422 NOT_ENOUGH_RESOURCES.

create table campaign_quest (
    campaign_id        bigint      not null references campaign(id) on delete cascade,
    quest_number       smallint    not null references quest_def(number),
    status             varchar(12) not null check (status in ('OPEN','COMPLETED','EXPIRED')),
    opened_in_chapter  smallint    not null,
    closed_in_chapter  smallint,                          -- когда выполнено или истекло
    updated_at         timestamptz not null default now(),
    primary key (campaign_id, quest_number)               -- задание в кампании один раз (как индекс 8→9 в app)
);

create table campaign_achievement (
    id                  bigint generated always as identity primary key,
    campaign_id         bigint       not null references campaign(id) on delete cascade,
    achievement_code    varchar(48)  references achievement_def(code),   -- null: пользовательское
    name                varchar(100) not null,           -- отображаемое название
    normalized_name     varchar(100) not null,           -- lower, ё→е, схлопнутые пробелы (42.1)
    source              varchar(12)  not null check (source in ('QUEST','CHAPTER','DECISION','MANUAL')),
    granted_in_chapter  smallint     not null,
    granted_at          timestamptz  not null default now(),
    unique (campaign_id, normalized_name)                 -- одно достижение на кампанию (D-9)
);
create unique index ux_campaign_achievement_code
    on campaign_achievement (campaign_id, achievement_code) where achievement_code is not null;

create table campaign_battle (
    id                      uuid        primary key,    -- создаёт браузер при старте; повтор запросов не дублирует бой
    campaign_id             bigint      not null references campaign(id) on delete cascade,
    purpose                 varchar(12) not null check (purpose in ('PROLOGUE','QUEST','FREE','FINAL')),
    quest_number            smallint    references quest_def(number),
    boss_code               varchar(32) references boss(code),     -- null: параметры введены вручную
    difficulty              smallint    not null check (difficulty between 0 and 3),
    chapter                 smallint    not null check (chapter between 0 and 11),   -- глава на момент старта
    progress_seq            integer     not null,       -- campaign.progress_seq на момент старта
    status                  varchar(12) not null default 'IN_PROGRESS'
                              check (status in ('IN_PROGRESS','APPLIED','DISMISSED','ABANDONED')),
    result                  varchar(8)  check (result in ('VICTORY','DEFEAT')),
    defeat_reason           varchar(12) check (defeat_reason in ('ROUNDS','SURRENDER')),
    rounds_played           smallint    check (rounds_played between 1 and 10),
    overrides               jsonb,                       -- награды, исправленные через «Редактировать»
    started_by_device_id    uuid        references device(id) on delete set null,   -- кто начал
    submitted_by_device_id  uuid        references device(id) on delete set null,   -- кто отправил результат
    started_at              timestamptz not null,
    finished_at             timestamptz,
    submitted_at            timestamptz,
    check ((purpose = 'QUEST') = (quest_number is not null)),
    check ((status in ('APPLIED','DISMISSED')) = (result is not null)),
    check (defeat_reason is null or result = 'DEFEAT'),
    check (result is distinct from 'DEFEAT' or defeat_reason is not null)
);
create index ix_campaign_battle_campaign on campaign_battle (campaign_id, started_at desc);
create index ix_campaign_battle_active on campaign_battle (campaign_id) where status = 'IN_PROGRESS';

create table campaign_trophy (                          -- создаётся при принятии победы
    id                  bigint generated always as identity primary key,
    campaign_id         bigint      not null references campaign(id) on delete cascade,
    boss_code           varchar(32) not null references boss(code),
    chapter             smallint    not null,
    campaign_battle_id  uuid        unique references campaign_battle(id) on delete set null,
    acquired_at         timestamptz not null default now()
);

create table share_link (
    id           uuid        primary key,       -- токен = base64url(id ‖ HMAC(key, id)); сам токен не хранится
    campaign_id  bigint      not null references campaign(id) on delete cascade,
    created_by   bigint      not null references app_user(id) on delete cascade,
    permission   varchar(8)  not null default 'EDIT' check (permission in ('EDIT')),   -- задел на VIEW
    created_at   timestamptz not null default now(),
    revoked_at   timestamptz
);
create unique index ux_share_link_campaign on share_link (campaign_id)
    where revoked_at is null;                                          -- одна действующая ссылка

create table share_access (                      -- кто открыл ссылку
    id             bigint      generated always as identity primary key,
    share_link_id  uuid        not null references share_link(id) on delete cascade,
    user_id        bigint      references app_user(id) on delete cascade,  -- вошедший: доступ на всех его устройствах
    device_id      uuid        references device(id) on delete cascade,    -- гость: доступ только на этом устройстве
    joined_at      timestamptz not null default now(),
    check ((user_id is null) <> (device_id is null))
);
create unique index ux_share_access_user on share_access (share_link_id, user_id) where user_id is not null;
create unique index ux_share_access_device on share_access (share_link_id, device_id) where device_id is not null;











