package net.neoforged.waifu.db.sql.search;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface SqlFilter {
    String buildSql(String field, SqlArgumentContext ctx);

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

    static SqlFilter smallerThan(Object val) {
        return make((field, ctx) -> field + " < " + ctx.insert(val), lhs -> lhs + " < " + val);
    }

    static SqlFilter not(SqlFilter op) {
        return make((field, ctx) -> "not (" + op.buildSql(field, ctx) + ")",
                lhs -> "!(" + op.buildJson(lhs) + ")");
    }

    static SqlFilter parseDateTime(Map<String, Object> in) {
        var after = (OffsetDateTime) in.get("after");
        if (after != null) {
            return SqlFilter.greaterThan(after);
        }
        return SqlFilter.smallerThan(in.get("before"));
    }

    @SuppressWarnings("unchecked")
    static SqlFilter parse(Object in) {
        if (in instanceof Map<?,?> filter) {
            var equals = filter.get("equals");
            if (equals != null) return SqlFilter.eq(equals);

            var matches = filter.get("matches");
            if (matches != null) return SqlFilter.matches((String) matches);

            var startsWith = filter.get("startsWith");
            if (startsWith != null) return SqlFilter.startsWith((String) startsWith);

            var allOf = (List<Map<String, Object>>) filter.get("allOf");
            if (allOf != null) {
                return SqlFilter.allOf(allOf.stream().map(SqlFilter::parse).toList());
            }

            var anyOf = (List<Map<String, Object>>) filter.get("anyOf");
            if (anyOf != null) {
                return SqlFilter.anyOf(anyOf.stream().map(SqlFilter::parse).toList());
            }

            var not = (Map<String, Object>) filter.get("not");
            return SqlFilter.not(parse(not));
        }
        return SqlFilter.eq(in);
    }

    static SqlFilter make(BiFunction<String, SqlArgumentContext, String> sql, Function<String, String> json) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlArgumentContext ctx) {
                return sql.apply(field, ctx);
            }

            @Override
            public String buildJson(String lhs) {
                return json.apply(lhs);
            }
        };
    }
}
