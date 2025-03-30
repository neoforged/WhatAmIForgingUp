create index idx_methods_by_cls on methods(cls);
create index idx_fields_by_cls on fields(cls);

create index idx_method_defs_by_type on method_defs(type);
create index idx_field_defs_by_type on field_defs(type);
