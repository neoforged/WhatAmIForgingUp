create table tokens
(
    name      text not null primary key,
    token     text not null,
    ratelimit text
);
