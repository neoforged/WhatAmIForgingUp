create table enum_extensions
(
    mod         int   not null,
    enum        int   not null references classes (id),
    name        int   not null references constants (id),
    constructor int   not null references constants (id),
    parameters  jsonb not null,
    foreign key (mod) references mods (id) on delete cascade
);

create index idx_enum_extensions_by_mod on enum_extensions(mod);
create index idx_enum_extensions_by_enum on enum_extensions(enum);

create or replace function insert_enum_extension(mod int, enm text, nm text, ctor text, params text)
    returns void
    language plpgsql
as
$function$
declare
    enumid integer;
    nameid integer;
    ctorid integer;
begin
    enumid := get_class_id(enm);
    nameid := get_constant(nm);
    ctorid := get_constant(ctor);
    insert into enum_extensions(mod, enum, name, constructor, parameters) values (mod, enumid, nameid, ctorid, params::jsonb);
end
$function$
;
