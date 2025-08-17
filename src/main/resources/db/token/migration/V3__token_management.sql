create table new_tokens
(
    name                 text not null primary key,
    active               bool not null,
    token                text,
    token_last_generated datetime,
    ratelimit            text,
    timeout              int
);

insert into new_tokens (name, token, ratelimit, timeout, token_last_generated, active) select name, token, ratelimit, timeout, datetime('now') as token_last_generated, true as active from tokens;

drop table tokens;

alter table new_tokens rename to tokens;
