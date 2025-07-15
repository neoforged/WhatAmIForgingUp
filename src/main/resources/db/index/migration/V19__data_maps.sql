create table data_maps
(
    mod      int     not null,
    data_map int     not null references constants (id),
    key      int     not null references constants (id),
    value    int     not null references json_constants (id),
    replace  boolean not null,
    foreign key (mod) references mods (id) on delete cascade
);

create index idx_data_maps_by_mod on data_maps (mod);
create index idx_data_maps_by_data_map on data_maps (data_map);
create index idx_data_maps_by_key on data_maps (key);

create or replace function insert_data_map(mod int, nm text, entries text[])
    returns void
    language plpgsql
as
$function$
declare
    nameid     integer;
    entry      text;
    entry_json jsonb;
begin
    nameid := get_constant(nm);
    foreach entry in array entries
        loop
            entry_json := entry::jsonb;
            insert into data_maps(mod, data_map, key, value, replace)
            values (mod, nameid, get_constant(entry_json ->> 'key'),
                    get_json_constant(entry_json -> 'value'), (entry_json ->> 'replace')::bool);
        end loop;
end
$function$;
