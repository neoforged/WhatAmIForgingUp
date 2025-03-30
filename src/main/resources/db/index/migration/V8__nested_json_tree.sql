create or replace function migrate_nested_tree_to_json(tree text)
    returns jsonb
    language plpgsql
as
$function$
declare
    js jsonb;
    ch text;
    curname text;
    nestedlevel int;
    nestedtext text;
begin
    if tree is null or tree = '' then
        return null;
    end if;

    js := '[]'::jsonb;

    curname := '';
    nestedtext := '';
    nestedlevel := 0;

    foreach ch in array regexp_split_to_array(tree, '')
        loop
            if ch = ' ' then
                continue;
            elsif ch = '(' then
                -- We only add the parantheses when we're already nested so we can correctly parse it
                if nestedlevel > 0 then nestedtext = nestedtext || ch; end if;
                nestedlevel := nestedlevel + 1;
            elsif ch = ')' then
                if nestedlevel > 0 then nestedtext = nestedtext || ch; end if;
                nestedlevel := nestedlevel - 1;
            elsif ch = ',' and nestedlevel <= 0 then
                js := jsonb_set(js, ARRAY[jsonb_array_length(js)::text], jsonb_strip_nulls(json_object('id': curname, 'nested': migrate_nested_tree_to_json(nestedtext))::jsonb), true);
                curname := '';
                nestedtext := '';
            elsif nestedlevel > 0 then
                nestedtext = nestedtext || ch;
            else
                curname = curname || ch;
            end if;
        end loop;

    js := jsonb_set(js, ARRAY[jsonb_array_length(js)::text], jsonb_strip_nulls(json_object('id': curname, 'nested': migrate_nested_tree_to_json(nestedtext))::jsonb), true);

    return js;
end
$function$
;

alter table mods
    alter column nested_tree set data type jsonb
    using migrate_nested_tree_to_json(nested_tree);
