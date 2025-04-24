package net.neoforged.waifu.db.sql.search;

import graphql.schema.SelectedField;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DatabaseType {
    private final DatabaseSchema schema;

    final String tableName;
    private final String groupBy;

    private final Map<String, QueryBuilder> fields;
    private final Map<String, FilterCriterion> filters;

    public DatabaseType(DatabaseSchema schema, String tableName, String groupBy, Map<String, QueryBuilder> fields, Map<String, FilterCriterion> filters) {
        this.schema = schema;
        this.tableName = tableName;
        this.groupBy = groupBy;
        this.fields = fields;
        this.filters = filters;
    }

    public SqlSearchBuilder createQuery() {
        return new SqlSearchBuilder(tableName);
    }

    public void applyFilter(SqlSearchBuilder builder, Map<String, Object> filter) {
        builder.where(SqlCondition.parseAsCriterion(filter, this.filters));
    }

    @SuppressWarnings("unchecked")
    public void apply(SqlSearchBuilder builder, SelectedField selectedField) {
        for (SelectedField immediateField : selectedField.getSelectionSet().getImmediateFields()) {
            var fld = fields.get(immediateField.getName());
            if (fld != null) {
                fld.query(this, builder, columnAlias(immediateField), immediateField);
            }
        }

        var filter = (Map<String, Object>) selectedField.getArguments().get("where");
        if (filter != null) {
            applyFilter(builder, filter);
        }
    }

    private String columnAlias(SelectedField field) {
        if (field.getAlias() == null) {
            return field.getName();
        }
        return "$" + field.getAlias();
    }

    public interface QueryBuilder {
        void query(DatabaseType type, SqlSearchBuilder builder, String fieldName, SelectedField selection);

        static QueryBuilder subTable(DatabaseType schema) {
            return (topLevel, builder, fieldName, selection) -> builder.columnSubQuery(schema.tableName, fieldName, sub -> schema.apply(sub, selection));
        }

        static QueryBuilder listSubTable(String typeName) {
            return (topLevel, builder, fieldName, selection) -> {
                var type = topLevel.schema.getType(typeName);
                builder.arrayAggregateSubQuery(type.tableName, fieldName, sub -> {
                    sub.where(topLevel.schema.getJoinRule(builder.table, typeName));
                    type.apply(sub, selection);
                });
            };
        }

        static QueryBuilder column(String column) {
            return (schema, builder, fieldName, selection) -> {
                builder.requestColumn(column, fieldName);
                var spl = column.split("\\.");
                if (spl.length == 2 && !spl[0].equals(schema.tableName)) {
                    schema.schema.possiblyJoin(builder, spl[0]);
                }
            };
        }
    }


    public static final class Builder {
        private final DatabaseSchema schema;
        private final String tableName;

        public Builder(DatabaseSchema schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        private String groupBy;
        private final Map<String, QueryBuilder> fields = new HashMap<>();
        private final Map<String, FilterCriterion> filters = new HashMap<>();


        public Builder field(String field, QueryBuilder query) {
            fields.put(field, query);
            return this;
        }

        public Builder directFields(String... fields) {
            for (String field : fields) {
                this.fields.put(field, QueryBuilder.column(tableName + "." + field));
            }
            return this;
        }

        public Builder field(String field, String column) {
            fields.put(field, QueryBuilder.column(column));
            return this;
        }

        public Builder filter(String filterType, FilterCriterion criterion) {
            filters.put(filterType, criterion);
            return this;
        }

        public Builder filterOnColumn(String filterType, String column) {
            var spl = column.split("\\.");
            if (spl.length == 2 && !spl[0].equals(this.tableName)) {
                filters.put(filterType, value -> ctx -> {
                    schema.possiblyJoin(ctx, spl[0]);
                    return SqlFilter.parse(value).buildSql(column, ctx);
                });
            } else {
                filters.put(filterType, FilterCriterion.column(column));
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder filterOnTable(String filterType, String table) {
            filters.put(filterType, value -> ctx -> {
                var conversion = schema.findShortestJoinPath(this.tableName, table);
                var sub = ctx.subBuilder(conversion.get(1));
                sub.where(schema.getJoinRule(this.tableName, conversion.get(1)));
                for (int i = 2; i < conversion.size(); i++) {
                    schema.join(sub, conversion.get(i - 1), conversion.get(i));
                }

                return "exists (" + sub.requestColumn("*", null).where(SqlCondition.parseAsCriterion((Map<String, Object>) value, schema.getType(table).filters)).format() + ")";
            });
            return this;
        }

        public Builder filters(Map<String, FilterCriterion> criteria) {
            filters.putAll(criteria);
            return this;
        }

        public Builder groupBy(String groupBy) {
            this.groupBy = groupBy;
            return this;
        }

        public DatabaseType build() {
            return new DatabaseType(schema, tableName, groupBy, Collections.unmodifiableMap(fields), Collections.unmodifiableMap(filters));
        }
    }
}
