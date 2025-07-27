package net.neoforged.waifu.db.sql.search;

import graphql.schema.SelectedField;
import org.intellij.lang.annotations.Language;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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

        default void applyOrder(DatabaseType type, SqlSearchBuilder builder, Map<String, Object> order) {

        }

        static QueryBuilder subTable(DatabaseType schema) {
            return subTable(schema.tableName);
        }

        static QueryBuilder subTable(String type) {
            return (topLevel, builder, fieldName, selection) -> {
                var schema = topLevel.schema.getType(type);
                builder.columnSubQuery(schema.tableName, fieldName, sub -> {
                    sub.where(topLevel.schema.getJoinRule(builder.table, schema.tableName));
                    schema.apply(sub, selection);
                    sub.requestAsJson();
                });
            };
        }

        static QueryBuilder listSubTable(DatabaseType type) {
            return listSubTable(type.tableName);
        }

        static QueryBuilder listSubTable(String typeName) {
            return (topLevel, builder, fieldName, selection) -> {
                var type = topLevel.schema.getType(typeName);
                builder.arrayAggregateSubQuery(type.tableName, fieldName, sub -> {
                    sub.where(topLevel.schema.getJoinRule(builder.table, typeName));
                    type.apply(sub, selection);
                    applyLimit(sub, selection);
                    DatabaseType.applyOrder(type, sub, selection);
                });
            };
        }

        static QueryBuilder directColumn(String column) {
            return new QueryBuilder() {
                @Override
                public void query(DatabaseType type, SqlSearchBuilder builder, String fieldName, SelectedField selection) {
                    builder.requestColumn(column, fieldName);
                }

                @Override
                public void applyOrder(DatabaseType type, SqlSearchBuilder builder, Map<String, Object> order) {
                    builder.orderBy(new SqlOrder(order).createStatement(column));
                }
            };
        }

        static QueryBuilder column(String column) {
            return new QueryBuilder() {
                @Override
                public void query(DatabaseType type, SqlSearchBuilder builder, String fieldName, SelectedField selection) {
                    possiblyJoin(type, builder);
                    builder.requestColumn(column.split("\\.").length == 1 ? type.tableName + "." + column : column, fieldName);
                }

                @Override
                public void applyOrder(DatabaseType type, SqlSearchBuilder builder, Map<String, Object> order) {
                    possiblyJoin(type, builder);
                    builder.orderBy(new SqlOrder(order).createStatement(column.split("\\.").length == 1 ? type.tableName + "." + column : column));
                }

                private void possiblyJoin(DatabaseType type, SqlSearchBuilder builder) {
                    var spl = column.split("\\.");
                    if (spl.length == 2 && !spl[0].equals(type.tableName)) {
                        if (!builder.isJoined(spl[0])) {
                            type.schema.possiblyJoin(builder, spl[0]);
                        }
                    }
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
                this.fields.put(field, QueryBuilder.directColumn(tableName + "." + field));
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
            return filterOnColumn(filterType, column, SqlFilter.STRING_FILTER);
        }

        public Builder filterOnColumn(String filterType, String column, SqlFilter.FilterType type) {
            var spl = column.split("\\.");
            if (spl.length == 2 && !spl[0].equals(this.tableName)) {
                filters.put(filterType, value -> ctx -> {
                    schema.possiblyJoin(ctx, spl[0]);
                    return type.parse(value).buildSql(column, ctx);
                });
            } else {
                filters.put(filterType, FilterCriterion.column(column, type));
            }
            return this;
        }

        public Builder filterOnTable(String filterType, DatabaseType type) {
            return this.filterOnTable(filterType, type.tableName);
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

        @SuppressWarnings("unchecked")
        public Builder filterWithSubQuery(String filterType, @Language("sql") String selector, Map<String, FilterCriterion> criteria) {
            filters.put(filterType, value -> {
                var condition = SqlCondition.parseAsCriterion((Map<String, Object>) value, criteria);
                return ctx ->
                        "exists (select 1 from " + selector + " where " + condition.build(ctx) + ")";
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

    public static void applyLimit(SqlSearchBuilder builder, SelectedField selection) {
        var limit = (Integer) selection.getArguments().get("limit");
        if (limit != null) {
            builder.limit(limit);
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyOrder(DatabaseType type, SqlSearchBuilder builder, SelectedField selection) {
        var order = (Map<String, Map<String, Object>>) selection.getArguments().get("order");
        if (order != null && order.entrySet().size() == 1) {
            var orderEntry = order.entrySet().stream().findFirst().orElseThrow();
            var field = orderEntry.getKey();
            type.fields.get(field).applyOrder(type, builder, orderEntry.getValue());
        }
    }
}
