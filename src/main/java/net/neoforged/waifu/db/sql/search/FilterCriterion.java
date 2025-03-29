package net.neoforged.waifu.db.sql.search;

import java.util.Map;

@FunctionalInterface
public interface FilterCriterion {
    SqlCondition apply(Object value);

    static FilterCriterion column(String col) {
        return value -> SqlCondition.columnFilter(SqlFilter.parse(value), col);
    }

    static FilterCriterion jsonExpression(String column, String expression) {
        return value -> SqlCondition.jsonFilter(SqlFilter.parse(value), column, expression);
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
}
