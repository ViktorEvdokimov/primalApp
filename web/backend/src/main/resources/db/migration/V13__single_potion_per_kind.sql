-- Зелье каждого вида у охотника одно: повторное приготовление повышает уровень имеющегося (лаборатория).
-- Повторы, накопленные раньше, сводятся к одной карте — с наибольшим уровнем (при равных — первой полученной).
delete from hunter_item
where id in (
    select id
    from (select id,
                 row_number() over (partition by hunter_id, lower(name) order by level desc nulls last, id) as rank
          from hunter_item
          where kind = 'POTION') ranked
    where ranked.rank > 1);

create unique index ux_hunter_item_potion on hunter_item (hunter_id, lower(name)) where kind = 'POTION';
