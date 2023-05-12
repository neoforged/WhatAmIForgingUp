CREATE OR REPLACE FUNCTION get_children_interface(tb text, iface text)
 returns table(class text, interfaces text[], methods text[], super text, modid integer, modname varchar(63), projectId int, projectIdId int)
 LANGUAGE plpgsql
AS $function$
begin
    return query execute format('with recursive parents as
	(
	  select
	    "class",
	    interfaces,
	    methods,
	    super,
	    modid
	  from %I."inheritance"
	  where
	    $1 = any(interfaces)
	  union
	  select
	    child."class",
	    child.interfaces,
	    child.methods,
	    child.super,
	    child.modid
	  from %I."inheritance" child
	    inner join parents p on p."class" = any(child.interfaces) or p."class" = child.super
	)
    select * from parents
	left join %I."modids" as "modids__via__modid" on "parents"."modid" = "modids__via__modid"."id"
	limit 1048575;', tb, tb, tb) using iface;
end
    $function$
;

CREATE OR REPLACE FUNCTION get_children_superclass(tb text, superClass text)
 returns table(class text, interfaces text[], methods text[], super text, modid integer, modname varchar(63), projectId int, projectIdId int)
 LANGUAGE plpgsql
AS $function$
begin
    return query execute format('with recursive parents as
	(
	  select
	    "class",
	    interfaces,
	    methods,
	    super,
	    modid
	  from %I."inheritance"
	  where
	    $1 = super
	  union
	  select
	    child."class",
	    child.interfaces,
	    child.methods,
	    child.super,
	    child.modid
	  from %I."inheritance" child
	    inner join parents p on p."class" = child.super
	)
    select * from parents
	left join %I."modids" as "modids__via__modid" on "parents"."modid" = "modids__via__modid"."id"
	limit 1048575;', tb, tb, tb) using superClass;
end
    $function$
;
