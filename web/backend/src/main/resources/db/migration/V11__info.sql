-- Раздел «Инфо» (qa № 142): ключевые слова правил, символы реакций монстров, жетоны окружения. Статьи создаёт и
-- правит администратор (ключевые слова — ещё и импортом из файла правил); у статьи может быть картинка.
create table info_image (
    id            uuid        primary key,
    content_type  varchar(32) not null check (content_type in ('image/png','image/jpeg','image/webp','image/gif')),
    data          bytea       not null,
    created_at    timestamptz not null default now()
);

create table info_entry (
    id          bigint generated always as identity primary key,
    section     varchar(16)  not null check (section in ('KEYWORDS','REACTIONS','TOKENS')),
    title       varchar(120) not null,
    body        text         not null default '',
    image_id    uuid         references info_image(id) on delete set null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    updated_by  bigint       references app_user(id) on delete set null
);
-- Название в разделе не повторяется (без учёта регистра)
create unique index ux_info_entry_title on info_entry (section, lower(title));
