create table recipes
(
    mod   int   not null,
    name  int   not null references constants (id),
    type  int   not null references constants (id),
    value jsonb not null,
    foreign key (mod) references mods (id) on delete cascade
);

create index idx_recipes_by_mod on recipes (mod);
create index idx_recipes_by_name on recipes (name);
create index idx_recipes_by_type on recipes (type);

create table data_files
(
    mod   int   not null,
    path  int   not null references constants (id),
    value jsonb not null,
    foreign key (mod) references mods (id) on delete cascade
);

create index idx_data_files_by_mod on data_files (mod);
create index idx_data_files_by_path on data_files (path);
