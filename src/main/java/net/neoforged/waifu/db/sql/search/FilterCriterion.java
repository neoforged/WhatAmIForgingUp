package net.neoforged.waifu.db.sql.search;

import java.util.Map;

@FunctionalInterface
public interface FilterCriterion {
    SqlCondition apply(Object value);

    static FilterCriterion column(String col) {
        return column(col, SqlFilter.STRING_FILTER);
    }

    static FilterCriterion column(String col, SqlFilter.FilterType type) {
        return value -> SqlCondition.columnFilter(type.apply(value), col);
    }

    static FilterCriterion jsonExpression(String column, String expression) {
        return value -> SqlCondition.jsonFilter(SqlFilter.STRING_FILTER.apply(value), column, expression);
    }

    @FunctionalInterface
    interface MapOnly extends FilterCriterion {
        @Override
        @SuppressWarnings("unchecked")
        default SqlCondition apply(Object value) {
            return apply((Map<String, Object>) value);
        }

        SqlCondition apply(Map<String, Object> value);
    }

    static FilterCriterion subFilters(Map<String, FilterCriterion> criteria) {
        return (MapOnly) value -> SqlCondition.parseAsCriterion(value, criteria);
    }
}
