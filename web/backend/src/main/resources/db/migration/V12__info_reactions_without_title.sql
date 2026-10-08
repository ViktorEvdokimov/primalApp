-- Символы реакций монстров — без названия, только картинка и описание (qa № 143): в правилах названий у них нет.
-- У остальных разделов название обязательно и не повторяется в разделе.
alter table info_entry alter column title drop not null;
alter table info_entry add constraint info_entry_title_required check (title is not null or section = 'REACTIONS');
drop index ux_info_entry_title;
create unique index ux_info_entry_title on info_entry (section, lower(title)) where title is not null;
