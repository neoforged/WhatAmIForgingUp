create index idx_children_by_parent on class_parents(parent);

create index idx_method_references_by_reference on method_references(reference);
create index idx_field_references_by_reference on field_references(reference);
