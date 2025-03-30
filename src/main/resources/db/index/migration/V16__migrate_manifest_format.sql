create or replace function migrate_manifest_format(man jsonb)
    returns jsonb
    language plpgsql
as
$function$
declare
    result jsonb;
    subobject jsonb;
    entry record;
    subentry record;
begin
    result := '[]'::jsonb;
    for entry in select key, value from jsonb_each(man)
        loop
            subobject := '[]'::jsonb;
            for subentry in select key, value from jsonb_each(entry.value)
                loop
                    subobject := jsonb_set(subobject, array[jsonb_array_length(subobject)::text], jsonb_build_object('key', subentry.key, 'value', subentry.value), true);
                end loop;
            result := jsonb_set(result, array[jsonb_array_length(result)::text], jsonb_build_object('name', entry.key, 'attributes', subobject), true);
        end loop;
    return result;
end
$function$
;

alter table mods
    alter column manifest set data type jsonb
    using migrate_manifest_format(manifest);

drop function migrate_manifest_format(jsonb);
