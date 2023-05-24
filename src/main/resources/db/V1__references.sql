create table projects
(
    projectId int not null primary key,
    fileId    int not null
);

create table modids
(
    modId     varchar(63) not null,
    projectId int         not null references projects (projectId),
    id        serial unique,
    constraint modids_pk primary key (modId, projectId)
);

create table refs
(
    modId  int      not null references modids (id),
    amount int      not null,
    owner  text     not null,
    member text     not null,
    type   smallint not null,
    constraint references_pk primary key (modId, owner, member, type)
);

create table inheritance
(
    modId      int     not null references modids (id),
    class      text    not null,
    super      text, -- if null, it's java/lang/Object
    interfaces text [] not null,
    methods    text [] not null,
    constraint inheritance_pk primary key (modId, class)
);