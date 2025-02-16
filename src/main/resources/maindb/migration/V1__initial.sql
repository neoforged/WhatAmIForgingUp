create table indexed_game_versions
(
    version text not null,
    loader text not null,
    constraint indexed_game_versions_pk primary key (version, loader)
);
