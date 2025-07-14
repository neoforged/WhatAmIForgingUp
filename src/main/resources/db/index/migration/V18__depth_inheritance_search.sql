drop function get_child_classes(int);
drop function get_child_classes(text);

create or replace function get_child_classes(cid int)
returns table (
	type int,
	depth int
) language sql as
$func$
with recursive parents as (
	select distinct(class_defs.type), 1 as depth from class_parents
	inner join class_defs on class_parents.cls = class_defs.id
	where class_parents.parent = cid
	union

	select class_defs.type, mc.depth from (
		select distinct(child.cls), (p.depth + 1) as depth from class_parents child
        inner join parents p on p.type = child.parent
	) mc
	inner join class_defs on mc.cls = class_defs.id
)
select * from parents
$func$
stable;

create or replace function get_child_classes(cname text)
returns table (
	type int,
	depth int
) language sql as
$func$
select ch.* from classes
	join get_child_classes(classes.id) ch on true
	where classes.name = cname
$func$
stable;