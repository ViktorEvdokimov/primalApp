-- Вход по логину и паролю вместо кода из письма (задача 8.1).
-- Логин — латиница, цифры, «.», «_», «-», хранится в нижнем регистре. Пароль — только хеш
-- ({bcrypt}…, PasswordEncoder Spring Security). Телефон — необязательный, в формате +79123456789;
-- позже по нему будет восстановление доступа.
alter table app_user add column login varchar(32);
alter table app_user add column password_hash varchar(100);   -- null: пароль ещё не задан (аккаунт по почте)
alter table app_user add column phone varchar(16);

-- Аккаунты, созданные по почте, получают логин user<id>; пароль они задают в настройках на запомненном
-- устройстве. Почта остаётся в БД, но больше не используется.
update app_user set login = 'user' || id where login is null;
alter table app_user alter column login set not null;
alter table app_user alter column email drop not null;
create unique index ux_app_user_login on app_user (login);

drop table login_challenge;
