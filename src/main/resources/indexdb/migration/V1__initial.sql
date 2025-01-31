create table classes
(
    id   serial primary key,
    name text not null unique
);

create table constants
(
    id       serial primary key,
    constant text not null unique
);

create table methods
(
    id         serial primary key,
    cls        int references classes (id),
    name       int not null references constants (id),
    descriptor int not null references constants (id)
);

create unique index idx_methods on methods (cls, name, descriptor);

create table fields
(
    id         serial primary key,
    cls        int references classes (id),
    name       int not null references constants (id),
    descriptor int references classes (id)
);

create unique index idx_fields on fields (cls, name, descriptor);

create table mods
(
    id                    serial primary key,
    version               text    not null,
    name                  text    not null,
    mod_ids               text[]  not null,
    license               text,
    loader                boolean not null default false,

    authors               text,
    update_json           text,
    nested_tree           text,
    language_loader       text,
    mods_toml             text,

    maven_coordinates     text,
    curseforge_project_id int,
    modrinth_project_id   varchar(8)
);

create table known_files
(
    mod  int  not null,
    sha1 text not null primary key,
    foreign key (mod) references mods (id) on delete cascade
);

create table known_curseforge_file_ids
(
    id int not null unique
);
create table known_modrinth_file_ids
(
    id varchar(8) not null unique
);

create table tags
(
    mod     int     not null,
    tag     int     not null references constants (id),
    entry   int     not null references constants (id),
    replace boolean not null,
    foreign key (mod) references mods (id) on delete cascade
);

create table class_defs
(
    id   serial primary key,
    type int references classes (id),
    mod  int not null,
    foreign key (mod) references mods (id) on delete cascade
);

create index idx_class_defs on class_defs (mod);

create table class_parents
(
    cls    int,
    parent int references classes (id),
    foreign key (cls) references class_defs (id) on delete cascade,
    primary key (cls, parent)
);

create table method_defs
(
    id    serial primary key,
    owner int,
    type  int references methods (id),
    foreign key (owner) references class_defs (id) on delete cascade
);

create table field_defs
(
    id    serial primary key,
    owner int,
    type  int references fields (id),
    foreign key (owner) references class_defs (id) on delete cascade
);

create table method_references
(
    owner     int,
    reference int references methods (id),
    count     smallint not null,
    foreign key (owner) references class_defs (id) on delete cascade
);

create table field_references
(
    owner     int,
    reference int references fields (id),
    count     smallint not null,
    foreign key (owner) references class_defs (id) on delete cascade
);

create table class_references
(
    owner     int,
    reference int references classes (id),
    count     smallint not null,
    foreign key (owner) references class_defs (id) on delete cascade
);

create table class_annotations
(
    owner      int,
    annotation int references classes (id),
    value      int not null references constants (id),
    foreign key (owner) references class_defs (id) on delete cascade
);

create table method_annotations
(
    owner      int,
    annotation int references classes (id),
    value      int not null references constants (id),
    foreign key (owner) references method_defs (id) on delete cascade
);

create table field_annotations
(
    owner      int,
    annotation int references classes (id),
    value      int not null references constants (id),
    foreign key (owner) references field_defs (id) on delete cascade
);

CREATE OR REPLACE FUNCTION get_class_id(nm text)
    returns int
    LANGUAGE plpgsql
AS
$function$
declare
    existing integer;
begin
    select id into existing from classes where name = nm for update;
    if existing is null then
        insert into classes(name) values (nm) returning id into existing;
    end if;
    return existing;
end
$function$
;

CREATE OR REPLACE FUNCTION get_constant(const text)
    returns int
    LANGUAGE plpgsql
AS
$function$
declare
    existing integer;
begin
    select id into existing from constants where constant = const for update;
    if existing is null then
        insert into constants(constant) values (const) returning id into existing;
    end if;
    return existing;
end
$function$
;

CREATE OR REPLACE FUNCTION get_method_id(owner text, nm text, metdescin text)
    returns int
    LANGUAGE plpgsql
AS
$function$
declare
    ownerid  integer;
    existing integer;
    metdesc  integer;
    metname  integer;
begin
    ownerid := get_class_id(owner);
    metdesc := get_constant(metdescin);
    metname := get_constant(nm);

    select id into existing from methods where cls = ownerid and name = metname and descriptor = metdesc;

    if existing is null then
        insert into methods(cls, name, descriptor) values (ownerid, metname, metdesc) returning id into existing;
    end if;
    return existing;
end
$function$
;

CREATE OR REPLACE FUNCTION get_field_id(owner text, nm text, fielddesc text)
    returns int
    LANGUAGE plpgsql
AS
$function$
declare
    ownerid   integer;
    existing  integer;
    typeid    integer;
    fieldname integer;
begin
    ownerid := get_class_id(owner);
    typeid := get_class_id(fielddesc);
    fieldname := get_constant(nm);

    select id into existing from fields where cls = ownerid and name = fieldname and descriptor = typeid;

    if existing is null then
        insert into fields(cls, name, descriptor) values (ownerid, fieldname, typeid) returning id into existing;
    end if;
    return existing;
end
$function$
;

CREATE OR REPLACE FUNCTION insert_class(mod int, name text, super text, interfaces text[], annotations text, fields text, methods text,
                                        refs text)
    returns int
    LANGUAGE plpgsql
AS
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
            insert into class_annotations(owner, annotation, value) values (cdef, get_class_id(ann ->> 0), get_constant(ann ->> 1));
        end loop;

    for fld in select * from json_array_elements(fields::json)
        loop
            insert into field_defs(owner, type) values (cdef, get_field_id(name, fld ->> 0, fld ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(fld -> 2)
                loop
                    insert into field_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_constant(ann ->> 1));
                end loop;
        end loop;

    for mtd in select * from json_array_elements(methods::json)
        loop
            insert into method_defs(owner, type) values (cdef, get_method_id(name, mtd ->> 0, mtd ->> 1)) returning id into memberid;

            for ann in select * from json_array_elements(fld -> 2)
                loop
                    insert into method_annotations(owner, annotation, value) values (memberid, get_class_id(ann ->> 0), get_constant(ann ->> 1));
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

create or replace function insert_tag(mod int, nm text, replace boolean, entries text[])
    returns void
    language plpgsql
AS
$function$
declare
    nameid integer;
    entry  text;
begin
    nameid := get_constant(nm);
    foreach entry in array entries
        loop
            insert into tags(mod, tag, replace, entry) values (mod, nameid, replace, get_constant(entry));
        end loop;
end
$function$
;

create or replace function get_child_classes(cid int)
    returns table
            (
                type int
            )
    language sql
as
$func$
with recursive parents as (select distinct (class_defs.type)
                           from class_parents
                                    inner join class_defs on class_parents.cls = class_defs.id
                           where class_parents.parent = cid

                           union

                           select (class_defs.type)
                           from (select distinct (child.cls)
                                 from class_parents child
                                          inner join parents p on p.type = child.parent) mc
                                    inner join class_defs on mc.cls = class_defs.id)
select type
from parents
$func$
;

CREATE OR REPLACE FUNCTION get_child_classes(name text)
    returns table
            (
                type int
            )
    LANGUAGE plpgsql
AS
$function$
declare
    cid integer;
begin
    select * into cid from get_class_id(name);
    return query select * from get_child_classes(cid);
end
$function$
;

create or replace function get_methods_overriding(cid int)
    returns table
            (
                id         int,
                cls        int,
                name       int,
                descriptor int
            )
    language sql
as
$func$
with owned_methods as
         (select id, cls, name, descriptor
          from methods
          where methods.cls = cid)
select overwritten.id, overwritten.cls, overwritten.name, overwritten.descriptor
from owned_methods
         inner join (select *
                     from get_child_classes(cid) as owners
                              inner join methods on methods.cls = owners.type)
    as overwritten on overwritten.name = owned_methods.name and overwritten.descriptor = owned_methods.descriptor
union
select *
from owned_methods
$func$
;

create or replace function get_methods_overriding(cid int, namepattern text)
    returns table
            (
                id         int,
                cls        int,
                name       int,
                descriptor int
            )
    language sql
as
$func$
with owned_methods as
         (select methods.id, methods.cls, methods.name, methods.descriptor
          from methods
                   inner join constants on constants.id = methods.name
          where methods.cls = cid
            and constants.constant ~ namepattern)
select overwritten.id, overwritten.cls, overwritten.name, overwritten.descriptor
from owned_methods
         inner join (select *
                     from get_child_classes(cid) as owners
                              inner join methods on methods.cls = owners.type)
    as overwritten on overwritten.name = owned_methods.name and overwritten.descriptor = owned_methods.descriptor
union
select *
from owned_methods
$func$
;

create or replace function get_tag_search_entry(registry text, tag text)
    returns text
    language plpgsql
as
$function$
declare
    nspace  text;
    tagname text;
begin
    nspace := split_part(tag, ':', 1);
    tagname := split_part(tag, ':', 2);

    return concat(nspace, '/', registry, '/', tagname);
end
$function$
;
