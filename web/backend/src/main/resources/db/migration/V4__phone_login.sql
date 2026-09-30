-- Номер телефона — логин (задача 8.2): вход по телефону и паролю, отдельного логина нет.
-- Телефон уникален. Повторы, которые могли появиться, пока он был необязательным и неуникальным,
-- остаются только у самого раннего аккаунта; остальные задают номер в настройках на запомненном устройстве.
update app_user set phone = null
where phone is not null
  and id not in (select min(id) from app_user where phone is not null group by phone);
create unique index ux_app_user_phone on app_user (phone);

drop index ux_app_user_login;
alter table app_user drop column login;
