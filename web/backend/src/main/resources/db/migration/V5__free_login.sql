-- Логин — свободное поле вместо номера телефона: латиница, цифры, «.», «_», «-», 3–32 символа,
-- хранится в нижнем регистре. Номер больше не логин и не хранится отдельно.
alter table app_user add column login varchar(32);

-- Старые аккаунты: логином становится телефон. Российский номер (+7XXXXXXXXXX) — в привычном виде
-- 8XXXXXXXXXX, остальные — цифры телефона без знака «+». Знаки, не входящие в правила логина, убираются.
update app_user set login =
    case
        when phone ~ '^\+7\d{10}$' then '8' || right(phone, 10)
        else lower(regexp_replace(phone, '[^A-Za-z0-9._-]', '', 'g'))
    end
where phone is not null;

-- Аккаунты, созданные по почте и оставшиеся без номера, получают логин user<id> (как в задаче 8.1):
-- они входят с запомненного устройства и меняют логин в настройках.
update app_user set login = 'user' || id where login is null;

alter table app_user alter column login set not null;
create unique index ux_app_user_login on app_user (login);

drop index ux_app_user_phone;
alter table app_user drop column phone;
