-- Основной персонаж участника кампании (qa № 150): охотник, выбранный по умолчанию в листе, кузнице и лаборатории.
-- Свой у каждого участника: у пользователя — на всех его устройствах, у гостя — на этом устройстве.
create table campaign_main_hunter (
    id            bigint      generated always as identity primary key,
    campaign_id   bigint      not null references campaign(id) on delete cascade,
    user_id       bigint      references app_user(id) on delete cascade,
    device_id     uuid        references device(id) on delete cascade,
    hunter_class  varchar(16) not null check (hunter_class in
                    ('DAREON','MIRA','TOREG','LIONAR','KARA','HELEREN','DRUSK','ZARAIA')),
    updated_at    timestamptz not null default now(),
    check ((user_id is null) <> (device_id is null))
);
create unique index ux_campaign_main_hunter_user on campaign_main_hunter (campaign_id, user_id) where user_id is not null;
create unique index ux_campaign_main_hunter_device on campaign_main_hunter (campaign_id, device_id) where device_id is not null;
create index ix_campaign_main_hunter_device on campaign_main_hunter (device_id) where device_id is not null;
