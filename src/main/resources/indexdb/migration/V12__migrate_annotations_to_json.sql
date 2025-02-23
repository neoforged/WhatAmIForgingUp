create or replace function migrate_annotation_value_to_json(val text)
    returns jsonb
    language plpgsql
as
$function$
declare
    nestedlevel int;
    nestedtext text;
    inStr bool;

    anntype text;

    js jsonb;

    ch text;

    posenum text[];
begin
    val := btrim(val);
    if val = 'true' then
        return to_jsonb(true);
    elsif val = 'false' then
        return to_jsonb(false);
    end if;

    ch := substring(val for 1);

    inStr := false;

    if ch = '"' then
        return to_jsonb(substring(val from 2 for char_length(val) - 2));
    elsif ch = '[' then
        js := '[]'::jsonb;
        nestedtext := '';
        nestedlevel := 0;
        foreach ch in array regexp_split_to_array(substring(val from 2 for char_length(val) - 2), '')
            loop
                if ch = '"' then
                    inStr = not inStr;
                    nestedtext := nestedtext || ch;
                elsif ch = '(' or ch = '[' and not inStr then
                    nestedtext := nestedtext || ch;
                    nestedlevel := nestedlevel + 1;
                elsif ch = ')' or ch = ']' and not inStr then
                    nestedtext := nestedtext || ch;
                    nestedlevel := nestedlevel - 1;
                elsif ch = ',' and nestedlevel <= 0 and not inStr then
                    js := jsonb_set(js, array[jsonb_array_length(js)::text], migrate_annotation_value_to_json(nestedtext), true);
                    nestedtext := '';
                    inStr := false;
                else
                    nestedtext := nestedtext || ch;
                end if;
            end loop;

        js := jsonb_set(js, array[jsonb_array_length(js)::text], migrate_annotation_value_to_json(nestedtext), true);
        return js;
    elsif ch = '@' then
        anntype := substring(val from '[\w/$]+');
        js := migrate_annotation_to_json(substring(val from (1 + char_length(anntype) + 2) for (char_length(val) - 1 - char_length(anntype) - 2))); -- substring @<ann>(
        js := jsonb_set(js, array['_$tp'], to_jsonb(anntype), true);
        return js;
    elsif ch ~ '[0-9-]' then
        return to_jsonb(val::double precision);
    else
        posenum := regexp_split_to_array(val, ':');
        if array_length(posenum, 1) = 1 then
            return to_jsonb(replace(posenum[1], '.class', ''));
        else
            return json_object('enum': posenum[1], 'value': posenum[2])::jsonb;
        end if;
    end if;
end
$function$
;

create or replace function migrate_annotation_to_json(ann text)
    returns jsonb
    language plpgsql
as
$function$
declare
    js jsonb;
    ch text;
    curname text;
    curvalue text;

    nestedlevel int;

    inValue bool;
    inStr bool;
begin
    js := '{}'::jsonb;
    if ann is null or ann = '' then
        return js;
    end if;

    curname := '';
    nestedlevel := 0;
    curvalue := '';
    inStr := false;

    foreach ch in array regexp_split_to_array(ann, '')
        loop
            if ch = '"' then
                inStr := not inStr;
                curvalue := curvalue || ch;
            elsif ch = '=' and nestedlevel <= 0 and not inStr then
                inValue := true;
            elsif ch = '(' or ch = '[' and not inStr then
                nestedlevel := nestedlevel + 1;
                curvalue := curvalue || ch;
            elsif ch = ')' or ch = ']' and not inStr then
                nestedlevel := nestedlevel - 1;
                curvalue := curvalue || ch;
            elsif ch = ',' and nestedlevel <= 0 and not inStr then
                js := jsonb_set(js, array[btrim(curname)], migrate_annotation_value_to_json(curvalue), true);
                curname := '';
                curvalue := '';
                inValue := false;
                inStr := false;
            elsif inValue then
                curvalue = curvalue || ch;
            else
                curname = curname || ch;
            end if;
        end loop;

    js := jsonb_set(js, array[btrim(curname)], migrate_annotation_value_to_json(curvalue), true);
    return js;
end
$function$
;

create or replace function migrate_annotation_to_json(ann int)
    returns jsonb
    language plpgsql
as
$function$
declare
    val text;
begin
    select constants.constant into val from constants where id = ann;
    return migrate_annotation_to_json(val);
end
$function$
;

create table json_constants
(
    id       serial primary key,
    constant jsonb not null unique
);

create or replace function get_json_constant(const jsonb)
    returns int
    language plpgsql
as
$function$
declare
    existing integer;
begin
    select id into existing from json_constants where constant = const for update;
    if existing is null then
        insert into json_constants(constant) values (const) returning id into existing;
    end if;
    return existing;
end
$function$
;

drop index idx_class_annotations_by_owner;
drop index idx_method_annotations_by_owner;
drop index idx_field_annotations_by_owner;

alter table class_annotations
    drop constraint class_annotations_value_fkey;
alter table class_annotations
    alter column value set data type int
    using get_json_constant(migrate_annotation_to_json(value));
alter table class_annotations
    add constraint class_annotations_value_fkey foreign key (value) references json_constants(id);

alter table method_annotations
    drop constraint method_annotations_value_fkey;
alter table method_annotations
    alter column value set data type int
    using get_json_constant(migrate_annotation_to_json(value));
alter table method_annotations
    add constraint method_annotations_value_fkey foreign key (value) references json_constants(id);

alter table field_annotations
    drop constraint field_annotations_value_fkey;
alter table field_annotations
    alter column value set data type int
    using get_json_constant(migrate_annotation_to_json(value));
alter table field_annotations
    add constraint field_annotations_value_fkey foreign key (value) references json_constants(id);

create index idx_class_annotations_by_owner on class_annotations(owner);
create index idx_method_annotations_by_owner on method_annotations(owner);
create index idx_field_annotations_by_owner on field_annotations(owner);

drop function migrate_annotation_to_json(int);
drop function migrate_annotation_to_json(text);
drop function migrate_annotation_value_to_json(text);

create or replace function insert_class(mod int, name text, super text, interfaces text[], annotations text, fields text, methods text,
                                        refs text)
    returns int
    language plpgsql
as
$function$
declare
    cdef     integer;
    iface    text;
    fld      json;
    mtd      json;
    rf       json;
    refsJson json;

    memberid integer;

    ann json;
begin
    insert into class_defs(mod, type) values (mod, get_class_id(name)) returning id into cdef;
    if super is not null then
        insert into class_parents(cls, parent) values (cdef, get_class_id(super));
    end if;

    foreach iface in array interfaces
        loop
            insert into class_parents(cls, parent) values (cdef, get_class_id(iface));
        end loop;

    for ann in select * from json_array_elements(annotations::json)
        loop
            insert into class_annotations(owner, annotation, value) values (cdef, get_class_id(ann ->> 0), get_json_constant(ann -> 1));
        end loop;

    for fld in select * from json_array_elements(fields::json)
        loop
            insert into field_defs(owner, type) values (cdef, get_field_id(name, fld ->> 0, fld ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(fld -> 2)
                loop
                    insert into field_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_json_constant(ann -> 1));
                end loop;
        end loop;

    for mtd in select * from json_array_elements(methods::json)
        loop
            insert into method_defs(owner, type) values (cdef, get_method_id(name, mtd ->> 0, mtd ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(mtd -> 2)
                loop
                    insert into method_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_json_constant(ann -> 1));
                end loop;
        end loop;

    refsJson := refs::json;

    for rf in select * from json_array_elements(refsJson -> 0)
        loop
            insert into method_references(owner, reference, count)
            values (cdef, get_method_id(rf ->> 0, rf ->> 1, rf ->> 2), (rf ->> 3)::int::smallint);
        end loop;

    for rf in select * from json_array_elements(refsJson -> 1)
        loop
            insert into field_references(owner, reference, count)
            values (cdef, get_field_id(rf ->> 0, rf ->> 1, rf ->> 2), (rf ->> 3)::int::smallint);
        end loop;

    return cdef;
end
$function$
;
