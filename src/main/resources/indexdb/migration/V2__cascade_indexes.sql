create index idx_method_references_by_owner on method_references (owner);
create index idx_field_references_by_owner on field_references (owner);

create index idx_method_defs_by_owner on method_defs(owner);
create index idx_field_defs_by_owner on field_defs(owner);

create index idx_parents_by_class on class_parents(cls);

create index idx_class_annotations_by_owner on class_annotations(owner);
create index idx_method_annotations_by_owner on method_annotations(owner);
create index idx_field_annotations_by_owner on field_annotations(owner);
