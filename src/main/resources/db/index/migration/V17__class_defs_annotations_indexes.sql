create index idx_class_defs_by_type on class_defs (type);
create index idx_class_annotations_by_annotation on class_annotations (annotation);
create index idx_method_annotations_by_annotation on method_annotations (annotation);
create index idx_field_annotations_by_annotation on field_annotations (annotation);
