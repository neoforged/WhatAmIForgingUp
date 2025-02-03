alter table mods
    add index_date timestamp with time zone;

create table waifu_versions
(
    version        text primary key         not null,
    date_installed timestamp with time zone not null
);
