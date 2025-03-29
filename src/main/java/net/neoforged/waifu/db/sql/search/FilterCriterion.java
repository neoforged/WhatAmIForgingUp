package net.neoforged.waifu.db.sql.search;

import java.util.Map;

@FunctionalInterface
public interface FilterCriterion {
    SqlCondition apply(Map<String, Object> value);

    static FilterCriterion column(String col) {
        return value -> SqlCondition.columnFilter(SqlFilter.parse(value), col);
    }

    static FilterCriterion jsonExpression(String column, String expression) {
        return value -> SqlCondition.jsonFilter(SqlFilter.parse(value), column, expression);
    }
}
