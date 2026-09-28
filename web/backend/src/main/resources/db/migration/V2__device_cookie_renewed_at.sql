-- Когда cookie устройства выдавалась или продлевалась последний раз. Браузеры хранят cookie не дольше
-- 400 дней, поэтому сервер продлевает её, если с прошлого раза прошло больше 30 дней (задача 3.2).
alter table device add column cookie_renewed_at timestamptz not null default now();
