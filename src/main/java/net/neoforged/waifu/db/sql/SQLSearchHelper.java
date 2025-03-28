package net.neoforged.waifu.db.sql;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import graphql.schema.DataFetchingEnvironment;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.HashPrefixSqlParser;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.SqlStatements;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SQLSearchHelper implements DatabaseSearchHelper {
    private static final Map<String, Criterion> CRITERIA = Map.of(
            "name", new Criterion.ColumnCriterion("mods.name"),
            "authors", new Criterion.ColumnCriterion("mods.authors"),
            "license", new Criterion.ColumnCriterion("mods.license"),

            "description", new Criterion.JsonCriterion("mods.mod_metadata_json", "$.mods[*].description", "$.description"),
            "modId", new Criterion.JsonCriterion("mods.mod_metadata_json", "$.mods[*].modId", "$.id"),
            "anyClassName", new Criterion.ColumnCriterion("classes.name")
    );

    private static final Map<String, Criterion> CLASS_CRITERIA = Map.of(
            "name", new Criterion.ColumnCriterion("classes.name")
    );

    private static final Map<String, String> MOD_FIELD_MAPPING = Map.of(
            "name", "name",
            "authors", "authors",
            "license", "license",
            "id", "id",
            "version", "version",
            "curseforgeProjectId", "curseforge_project_id",
            "modrinthProjectId", "modrinth_project_id",
            "mavenCoordinates", "maven_coordinates"
    );

    private static final int MAX_ITEMS_PER_REQUEST = 500;

    private final Jdbi jdbi;
    private final ModLoader loader;

    public SQLSearchHelper(Jdbi jdbi, ModLoader loader) {
        this.jdbi = jdbi;
        this.loader = loader;
        jdbi.getConfig(SqlStatements.class).setSqlParser(new HashPrefixSqlParser());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getMods(DataFetchingEnvironment env) {
        var builder = new SqlSearchBuilder("mods");

        builder.requestColumn("mods.id");

        Map<String, String> columnRequests = new HashMap<>();

        MOD_FIELD_MAPPING.forEach((fld, dbMapping) -> {
            if (env.getSelectionSet().contains("mods/" + fld)) {
                builder.requestColumn("mods." + dbMapping);
                columnRequests.put(dbMapping, fld);
            }
        });

        var pagination = Optional.ofNullable(env.<Map<String, Integer>>getArgument("pagination"))
                .map(m -> new Pagination(
                        Math.min(m.getOrDefault("limit", MAX_ITEMS_PER_REQUEST), MAX_ITEMS_PER_REQUEST),
                        m.getOrDefault("after", -1)
                ))
                .orElse(Pagination.DEFAULT);

        if (pagination.after > 0) {
            builder.where("id", FilterOp.greaterThan(pagination.after));
        }
        builder.limit(pagination.limit() + 1);

        boolean classJoin = false;
        List<WhereClause> classJoinFilter = new ArrayList<>();
        if (env.getSelectionSet().contains("mods/classes")) {
            classJoin = true;
            var selection = env.getSelectionSet().getFields("mods/classes").getFirst();
            var filArgs = selection.getArguments().get("filter");
            if (filArgs != null) {
                classJoinFilter.add(parseFilters(CLASS_CRITERIA, (Map<String, Object>) filArgs, new HashSet<>()));
            }

            String aggIn = "classes.name";

            var order = (Map<String, Map<String, Object>>) selection.getArguments().get("order");
            if (order != null) {
                var name = order.get("name");
                if (name != null) {
                    aggIn = aggIn + " order by " + new Order(name).createStatement(aggIn);
                }
            }

            var limitText = "";
            var limit = (Integer) selection.getArguments().get("limit");
            if (limit != null) {
                limitText = "[1:" + limit + "]";
            }

            builder.requestColumn("(array_agg(" + aggIn + ")::text[])" + limitText + " as classes");
        }

        Map<String, Object> filter = env.getArgument("filter");
        if (filter != null) {
            var applied = new HashSet<String>();
            builder.where(parseFilters(CRITERIA, filter, applied));

            if (applied.contains("anyClassName")) {
                classJoin = true;
            }
        }

        if (classJoin) {
            builder.joinOn("class_defs", WhereClause.condition("class_defs.mod = mods.id"));
            builder.joinOn("classes", WhereClause.condition("classes.id = class_defs.type"));
            builder.joinOn("classes", classJoinFilter);

            builder.groupBy("mods.id");
        }

        builder.orderBy("mods.id");

        return jdbi.withHandle(handle -> builder.build(handle)
                .execute((statementSupplier, ctx) -> {
                    var rs = statementSupplier.get().getResultSet();
                    String[] columnIds = new String[builder.columns.size() + 1];
                    for (String field : builder.columns) {
                        var realCol = List.of(field.split(" as ")).getLast();
                        realCol = List.of(realCol.split("\\.")).getLast();
                        columnIds[rs.findColumn(realCol)] = columnRequests.getOrDefault(realCol, realCol);
                    }

                    var lst = new ArrayList<Map<String, Object>>(pagination.limit());

                    while (rs.next()) {
                        var entry = HashMap.<String, Object>newHashMap(builder.columns.size());
                        for (int i = 1; i <= builder.columns.size(); i++) {
                            var o = rs.getObject(i);
                            if (o instanceof Array ar) {
                                entry.put(columnIds[i], Arrays.asList((Object[])ar.getArray()));
                            } else {
                                entry.put(columnIds[i], o);
                            }
                        }
                        lst.add(entry);
                    }

                    boolean hasNext = false;

                    if (lst.size() > pagination.limit()) {
                        hasNext = true;
                        lst.removeLast();
                    }

                    var pag = HashMap.newHashMap(2);
                    pag.put("hasNextPage", hasNext);
                    if (!lst.isEmpty()) {
                        pag.put("endCursor", lst.getLast().get("id"));
                    }

                    return Map.of(
                            "mods", lst,
                            "pageInfo", pag
                    );
                }));
    }

    private record Pagination(int limit, int after) {
        public static final Pagination DEFAULT = new Pagination(MAX_ITEMS_PER_REQUEST, -1);
    }

    @SuppressWarnings("unchecked")
    private WhereClause parseFilters(Map<String, Criterion> crit, Map<String, Object> filter, Set<String> appliedCriteria) {
        var allOf = (List<Map<String, Object>>) filter.get("allOf");
        if (allOf != null) {
            return WhereClause.allOf(allOf.stream().map(f -> parseFilters(crit, f, appliedCriteria)).toList());
        }

        var anyOf = (List<Map<String, Object>>) filter.get("anyOf");
        if (anyOf != null) {
            return WhereClause.anyOf(anyOf.stream().map(f -> parseFilters(crit, f, appliedCriteria)).toList());
        }

        var not = (Map<String, Object>) filter.get("not");
        if (not != null) {
            return WhereClause.not(parseFilters(crit, not, appliedCriteria));
        }

        var criterion = filter.entrySet().stream().findFirst().orElseThrow();
        var fil = parseStringFilter((Map<String, Object>) criterion.getValue());
        appliedCriteria.add(criterion.getKey());
        return switch (crit.get(criterion.getKey())) {
            case Criterion.ColumnCriterion(var fld) -> WhereClause.columnFilter(fil, fld);
            case Criterion.JsonCriterion(var col, var neo, var fabric) ->
                    WhereClause.jsonFilter(fil, col, loader == ModLoader.FABRIC ? fabric : neo);
        };
    }

    @SuppressWarnings("unchecked")
    private FilterOp parseStringFilter(Map<String, Object> filter) {
        var equals = filter.get("equals");
        if (equals != null) return FilterOp.equals((String) equals);

        var matches = filter.get("matches");
        if (matches != null) return FilterOp.matches((String) matches);

        var allOf = (List<Map<String, Object>>) filter.get("allOf");
        if (allOf != null) {
            return FilterOp.allOf(allOf.stream().map(this::parseStringFilter).toList());
        }

        var anyOf = (List<Map<String, Object>>) filter.get("anyOf");
        if (anyOf != null) {
            return FilterOp.anyOf(anyOf.stream().map(this::parseStringFilter).toList());
        }

        var not = (Map<String, Object>) filter.get("not");
        return FilterOp.not(parseStringFilter(not));
    }

    private static final class SqlSearchBuilder {
        private final String table;
        private final Set<String> columns = new LinkedHashSet<>();
        private final Set<String> groups = new LinkedHashSet<>();
        private final List<WhereClause> filters = new ArrayList<>();
        private final ArgumentContext ctx = new ArgumentContext();
        private final Multimap<String, WhereClause> joins = Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);

        private String orderColumn;

        private int limit;

        private SqlSearchBuilder(String table) {
            this.table = table;
        }

        public SqlSearchBuilder requestColumn(String col) {
            columns.add(col);
            return this;
        }

        public SqlSearchBuilder where(String field, FilterOp op) {
            filters.add(WhereClause.columnFilter(op, field));
            return this;
        }

        public SqlSearchBuilder where(WhereClause clause) {
            filters.add(clause);
            return this;
        }

        public SqlSearchBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SqlSearchBuilder orderBy(String column) {
            orderColumn = column;
            return this;
        }

        public SqlSearchBuilder joinOn(String column, WhereClause filter) {
            joins.put(column, filter);
            return this;
        }

        public SqlSearchBuilder joinOn(String column, Iterable<WhereClause> filter) {
            joins.putAll(column, filter);
            return this;
        }

        public SqlSearchBuilder groupBy(String... columns) {
            groups.addAll(List.of(columns));
            return this;
        }

        public Query build(Handle handle) {
            StringBuilder builder = new StringBuilder("select ");
            builder.append(String.join(", ", columns))
                    .append(" from ")
                    .append(table);

            var joins = this.joins.asMap();
            joins.forEach((tb, fil) -> builder.append(" join ")
                    .append(tb)
                    .append(" on ")
                    .append(filters(fil, ctx)));

            if (!filters.isEmpty()) {
                builder.append(" where ")
                        .append(filters.stream()
                                .map(c -> c.build(ctx))
                                .collect(Collectors.joining(" and ")));
            }

            if (!groups.isEmpty()) {
                builder.append(" group by ")
                        .append(String.join(", ", groups));
            }

            if (orderColumn != null) {
                builder.append(" order by ").append(orderColumn);
            }

            if (limit > 0) {
                builder.append(" limit ").append(limit);
            }

            var q = handle.createQuery(builder.toString());
            for (int i = 0; i < ctx.args.size(); i++) {
                q.bind("i" + i, ctx.args.get(i));
            }
            return q;
        }

        private static String filters(Collection<WhereClause> filters, ArgumentContext ctx) {
            return filters.stream()
                    .map(c -> c.build(ctx))
                    .collect(Collectors.joining(" and "));
        }
    }

    private interface WhereClause {
        String build(ArgumentContext ctx);

        static WhereClause allOf(List<WhereClause> ops) {
            return ctx -> ops.stream().map(w -> "(" + w.build(ctx) + ")").collect(Collectors.joining(" and "));
        }

        static WhereClause anyOf(List<WhereClause> ops) {
            return ctx -> ops.stream().map(w -> "(" + w.build(ctx) + ")").collect(Collectors.joining(" or "));
        }

        static WhereClause not(WhereClause ops) {
            return ctx -> "not (" + ops.build(ctx) + ")";
        }

        static WhereClause columnFilter(FilterOp op, String col) {
            return ctx -> op.buildSql(col, ctx);
        }

        static WhereClause condition(String condition) {
            return ctx -> condition;
        }

        static WhereClause jsonFilter(FilterOp op, String col, String expression) {
            return ctx -> "jsonb_path_exists(" + col + ", (" + ctx.insert(expression + " ? (" + op.buildJson("@") + ")") + ")::jsonpath)";
        }
    }

    private interface FilterOp {
        String buildSql(String field, ArgumentContext ctx);

        String buildJson(String lhs);

        static FilterOp allOf(List<FilterOp> ops) {
            return make((field, ctx) -> ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" and ")),
                    lhs -> ops.stream().map(o -> "(" + o.buildJson(lhs) + ")").collect(Collectors.joining(" && ")));
        }

        static FilterOp anyOf(List<FilterOp> ops) {
            return make((field, ctx) -> ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" or ")),
                    lhs -> ops.stream().map(o -> "(" + o.buildJson(lhs) + ")").collect(Collectors.joining("|| ")));
        }

        static FilterOp equals(String value) {
            return make((field, ctx) -> field + " = " + ctx.insert(value), lhs -> lhs + " == \"" + value + "\"");
        }

        static FilterOp matches(String regex) {
            return make((field, ctx) -> field + " ~ " + ctx.insert(regex), lhs -> lhs + " like_regex \"" + regex + "\"");
        }

        static FilterOp greaterThan(int val) {
            return make((field, ctx) -> field + " > " + ctx.insert(val), lhs -> lhs + " > " + val);
        }

        static FilterOp not(FilterOp op) {
            return make((field, ctx) -> "not (" + op.buildSql(field, ctx) + ")",
                    lhs -> "!(" + op.buildJson(lhs) + ")");
        }

        static FilterOp make(BiFunction<String, ArgumentContext, String> sql, Function<String, String> json) {
            return new FilterOp() {
                @Override
                public String buildSql(String field, ArgumentContext ctx) {
                    return sql.apply(field, ctx);
                }

                @Override
                public String buildJson(String lhs) {
                    return json.apply(lhs);
                }
            };
        }
    }

    private static class ArgumentContext {
        private final List<Object> args = new ArrayList<>();
        public String insert(Object value) {
            args.add(value);
            return "#i" + (args.size() - 1);
        }
    }

    private sealed interface Criterion {
        record ColumnCriterion(String column) implements Criterion {}
        record JsonCriterion(String column, String neoforge, String fabric) implements Criterion {}
    }

    private record Order(OrderRule rule, boolean desc) {
        private Order(Map<String, Object> map) {
            this(OrderRule.valueOf(((String) map.getOrDefault("by", "value")).toUpperCase(Locale.ROOT)), Objects.equals(map.get("direction"), "desc"));
        }

        public String createStatement(String column) {
            return rule.apply(column) + " " + (desc ? "desc" : "asc");
        }
    }

    private enum OrderRule {
        VALUE {
            @Override
            public String apply(String in) {
                return in;
            }
        },
        LENGTH {
            @Override
            public String apply(String in) {
                return "length(" + in + ")";
            }
        };

        public abstract String apply(String in);
    }
}
