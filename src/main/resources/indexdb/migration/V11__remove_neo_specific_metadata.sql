alter table mods
    drop column update_json;

alter table mods
    drop column language_loader;

alter table mods
    rename column mods_toml to mod_metadata;
alter table mods
    rename column mods_toml_json to mod_metadata_json;
