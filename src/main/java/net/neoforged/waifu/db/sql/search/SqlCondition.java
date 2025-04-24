package net.neoforged.waifu.db.sql.search;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@FunctionalInterface
public interface SqlCondition {
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

    static SqlCondition columnFilter(SqlFilter op, String col) {
        return ctx -> op.buildSql(col, ctx);
    }

    static SqlCondition condition(String condition) {
        return ctx -> condition;
    }

    static SqlCondition jsonFilter(SqlFilter op, String col, String expression) {
        return ctx -> "jsonb_path_exists(" + col + ", (" + ctx.insert(expression + " ? (" + op.buildJson("@") + ")") + ")::jsonpath)";
    }

    static SqlCondition parseAsCriterion(Map<String, Object> filter, Map<String, FilterCriterion> criteria) {
        return parseAsCriterion(filter, criteria, null);
    }

    static SqlCondition parseAsCriterion(Map<String, Object> filter, Map<String, FilterCriterion> criteria, @Nullable Set<String> appliedCriteria) {
        return parseAsCriterion(filter, criteria::get, appliedCriteria);
    }

    static SqlCondition parseAsCriterion(Map<String, Object> filter, Function<String, FilterCriterion> criteria) {
        return parseAsCriterion(filter, criteria, null);
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

        var not = (Map<String, Object>) filter.get("not");
        if (not != null) {
            return SqlCondition.not(parseAsCriterion(not, criteria, appliedCriteria));
        }

        var criterion = filter.entrySet().stream().findFirst().orElseThrow();
        if (appliedCriteria != null) appliedCriteria.add(criterion.getKey());
        return criteria.apply(criterion.getKey()).apply(criterion.getValue());
    }
}
