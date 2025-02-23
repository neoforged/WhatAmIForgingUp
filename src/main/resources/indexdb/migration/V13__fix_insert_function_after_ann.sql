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
            insert into class_annotations(owner, annotation, value) values (cdef, get_class_id(ann ->> 0), get_json_constant((ann -> 1)::jsonb));
        end loop;

    for fld in select * from json_array_elements(fields::json)
        loop
            insert into field_defs(owner, type) values (cdef, get_field_id(name, fld ->> 0, fld ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(fld -> 2)
                loop
                    insert into field_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_json_constant((ann -> 1)::jsonb));
                end loop;
        end loop;

    for mtd in select * from json_array_elements(methods::json)
        loop
            insert into method_defs(owner, type) values (cdef, get_method_id(name, mtd ->> 0, mtd ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(mtd -> 2)
                loop
                    insert into method_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_json_constant((ann -> 1)::jsonb));
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