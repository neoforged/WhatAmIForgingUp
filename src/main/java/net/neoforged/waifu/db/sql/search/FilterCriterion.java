package net.neoforged.waifu.db.sql.search;

import java.util.Map;

@FunctionalInterface
public interface FilterCriterion {
    SqlCondition apply(Object value);

    static FilterCriterion column(String col) {
        return column(col, SqlFilter.STRING_FILTER);
    }

    static FilterCriterion column(String col, SqlFilter.FilterType type) {
        return value -> SqlCondition.columnFilter(col, type.parse(value));
    }

    static FilterCriterion jsonExpression(String column, String expression, SqlFilter.FilterType filterType) {
        return value -> SqlCondition.columnFilter(column, SqlFilter.jsonpathPredicate(expression, filterType, filterType.parse(value)));
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
