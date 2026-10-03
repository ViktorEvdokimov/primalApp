-- Инвентарь охотников (задача 9.3): карты снаряжения, зелий и наград, которые охотник держит в колодах.
-- Новые кампании получают стартовый набор по правилам; уже созданным кампаниям предметы не добавляются.
create table hunter_item (
    id          bigint generated always as identity primary key,
    hunter_id   bigint       not null references campaign_hunter(id) on delete cascade,
    kind        varchar(16)  not null check (kind in ('EQUIPMENT', 'POTION', 'REWARD')),
    name        varchar(100) not null,
    level       smallint     check (level between 1 and 3),      -- null: у карты награды уровня нет
    element     varchar(16),                                      -- стихия кузни: её можно получить, сбросив карту
    source      varchar(16),                                      -- FIRE_01, LAB_02, номер карты награды; null — вручную
    created_at  timestamptz  not null default now()
);
create index ix_hunter_item_hunter on hunter_item (hunter_id, id);
