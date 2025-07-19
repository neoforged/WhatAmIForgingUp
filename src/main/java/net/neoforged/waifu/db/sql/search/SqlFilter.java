package net.neoforged.waifu.db.sql.search;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface SqlFilter {
    FilterType STRING_FILTER = new FilterType(Map.of(
            "matches", v -> SqlFilter.matches((String) v),
            "startsWith", v -> SqlFilter.startsWith((String) v)
    ));

    FilterType INT_FILTER = new FilterType(Map.of(
            "lessThan", SqlFilter::smallerThan,
            "greaterThan", SqlFilter::greaterThan,
            "lessThanOrEqual", SqlFilter::smallerThanOrEqual,
            "greaterThanOrEqual", SqlFilter::greaterThanOrEqual,
            "isEven", v -> SqlFilter.isEven((boolean) v)
    ));

    FilterType JSON_FILTER = new FilterType(Map.of(
            "pathExists", v -> SqlFilter.jsonpath_exists((String) v)
    ));

    FilterType DATE_TIME_FILTER = new FilterType(Map.of(
            "after", SqlFilter::greaterThan,
            "before", SqlFilter::smallerThan
    ));

    String buildSql(String field, SqlSearchBuilder ctx);

    String buildJson(String lhs);

    static SqlFilter allOf(List<SqlFilter> ops) {
        return make((field, ctx) -> ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" and ")),
                lhs -> ops.stream().map(o -> "(" + o.buildJson(lhs) + ")").collect(Collectors.joining(" && ")));
    }

    static SqlFilter anyOf(List<SqlFilter> ops) {
        return make((field, ctx) -> ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" or ")),
                lhs -> ops.stream().map(o -> "(" + o.buildJson(lhs) + ")").collect(Collectors.joining("|| ")));
    }

    static SqlFilter eq(Object value) {
        return make((field, ctx) -> field + " = " + ctx.insert(value), lhs -> lhs + " == \"" + value + "\"");
    }

    static SqlFilter matches(String regex) {
        return make((field, ctx) -> field + " ~ " + ctx.insert(regex), lhs -> lhs + " like_regex \"" + regex + "\"");
    }

    static SqlFilter startsWith(String str) {
        return make((field, ctx) -> "starts_with(" + field + ", " + ctx.insert(str) + ")", lhs -> lhs + " starts with \"" + str + "\"");
    }

    static SqlFilter greaterThan(Object val) {
        return make((field, ctx) -> field + " > " + ctx.insert(val), lhs -> lhs + " > " + val);
    }

    static SqlFilter greaterThanOrEqual(Object val) {
        return make((field, ctx) -> field + " >= " + ctx.insert(val), lhs -> lhs + " >= " + val);
    }

    static SqlFilter smallerThan(Object val) {
        return make((field, ctx) -> field + " < " + ctx.insert(val), lhs -> lhs + " < " + val);
    }

    static SqlFilter smallerThanOrEqual(Object val) {
        return make((field, ctx) -> field + " <= " + ctx.insert(val), lhs -> lhs + " <= " + val);
    }

    static SqlFilter not(SqlFilter op) {
        return make((field, ctx) -> "not (" + op.buildSql(field, ctx) + ")",
                lhs -> "!(" + op.buildJson(lhs) + ")");
    }

    static SqlFilter isEven(boolean even) {
        var checkValue = even ? 0 : 1;
        return make((field, ctx) -> field + " % 2 = " + checkValue, lhs -> lhs + " % 2 == " + checkValue);
    }

    static SqlFilter jsonpath_exists(String path) {
        return make(
                (field, ctx) -> "jsonb_path_exists(" + field + ", " + ctx.insert(path) + "::jsonpath)",
                lhs -> lhs + " ? (" + path + ")"
        );
    }

    static SqlFilter make(BiFunction<String, SqlSearchBuilder, String> sql, Function<String, String> json) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                return sql.apply(field, ctx);
            }

            @Override
            public String buildJson(String lhs) {
                return json.apply(lhs);
            }
        };
    }

    @SuppressWarnings("unchecked")
    record FilterType(
            Map<String, Function<Object, SqlFilter>> additionalFilters) implements Function<Object, SqlFilter> {
        @Override
        public SqlFilter apply(Object in) {
            if (in instanceof Map<?, ?> filter) {
                var filterEntry = filter.entrySet().stream().findFirst().orElse(null);
                assert filterEntry != null;

                return switch ((String) filterEntry.getKey()) {
                    case "equals" -> SqlFilter.eq(filterEntry.getValue());
                    case "not" -> SqlFilter.not(apply(filterEntry.getValue()));

                    case "allOf" -> SqlFilter.allOf(((List<Map<String, Object>>) filterEntry.getValue())
                            .stream().map(this).toList());
                    case "anyOf" -> SqlFilter.anyOf(((List<Map<String, Object>>) filterEntry.getValue())
                            .stream().map(this).toList());

                    default -> additionalFilters.get(filterEntry.getKey()).apply(filterEntry.getValue());
                };
            }
            return SqlFilter.eq(in);
        }
    }
}
