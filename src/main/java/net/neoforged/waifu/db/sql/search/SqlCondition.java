package net.neoforged.waifu.db.sql.search;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@FunctionalInterface
public interface SqlCondition {
    SqlCondition TRUE = condition("true");

    String build(SqlSearchBuilder ctx);

    static SqlCondition allOf(List<SqlCondition> ops) {
        return ctx -> ops.stream().map(w -> "(" + w.build(ctx) + ")").collect(Collectors.joining(" and "));
    }

    static SqlCondition anyOf(List<SqlCondition> ops) {
        return ctx -> ops.stream().map(w -> "(" + w.build(ctx) + ")").collect(Collectors.joining(" or "));
    }

    static SqlCondition not(SqlCondition ops) {
        return ctx -> "not (" + ops.build(ctx) + ")";
    }

    static SqlCondition columnFilter(String col, SqlFilter op) {
        return ctx -> op.buildSql(col, ctx);
    }

    static SqlCondition equals(String a, String b) {
        return ctx -> a + " = " + b;
    }

    static SqlCondition condition(String condition) {
        return ctx -> condition;
    }

    static SqlCondition jsonFilter(SqlFilter op, String col, String expression, String extractMethod) {
        return ctx -> {
            final var jsonPredicate = op.buildJson(expression);
            if (jsonPredicate == null) {
                return "exists (select 0 from jsonb_path_query(" + col + ", " + ctx.insert(expression) + "::jsonpath) as elem where "
                        + op.buildSql(extractMethod, ctx) + ")";
            } else {
                return col + " @@ (" + ctx.insert(jsonPredicate) + "::jsonpath)";
            }
        };
    }

    static SqlCondition parseAsCriterion(Map<String, Object> filter, Map<String, FilterCriterion> criteria) {
        return parseAsCriterion(filter, criteria, null);
    }

    static SqlCondition parseAsCriterion(Map<String, Object> filter, Map<String, FilterCriterion> criteria, @Nullable Set<String> appliedCriteria) {
        return parseAsCriterion(filter, criteria::get, appliedCriteria);
    }

    @SuppressWarnings("unchecked")
    static SqlCondition parseAsCriterion(Map<String, Object> filter, Function<String, FilterCriterion> criteria, @Nullable Set<String> appliedCriteria) {
        var allOf = (List<Map<String, Object>>) filter.get("allOf");
        if (allOf != null) {
            return SqlCondition.allOf(allOf.stream().map(f -> parseAsCriterion(f, criteria, appliedCriteria)).toList());
        }

        var anyOf = (List<Map<String, Object>>) filter.get("anyOf");
        if (anyOf != null) {
            return SqlCondition.anyOf(anyOf.stream().map(f -> parseAsCriterion(f, criteria, appliedCriteria)).toList());
        }

        var noneOf = (List<Map<String, Object>>) filter.get("noneOf");
        if (noneOf != null) {
            return SqlCondition.allOf(noneOf.stream().map(f -> not(parseAsCriterion(f, criteria, appliedCriteria))).toList());
        }

        var not = (Map<String, Object>) filter.get("not");
        if (not != null) {
            return SqlCondition.not(parseAsCriterion(not, criteria, appliedCriteria));
        }

        var criterion = filter.entrySet().stream().findFirst().orElseThrow();
        if (appliedCriteria != null) appliedCriteria.add(criterion.getKey());
        return criteria.apply(criterion.getKey()).apply(criterion.getValue());
    }
}
