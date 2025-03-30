alter function get_child_classes(int) stable;
alter function get_methods_overriding(int) stable;
alter function get_methods_overriding(int, text) stable;

drop function get_child_classes(text);
create function get_child_classes(cname text)
    returns table
            (
                type int
            )
    language sql
as
$function$
select ch.type from classes
    join get_child_classes(classes.id) ch on true
    where classes.name = cname
$function$
stable;
