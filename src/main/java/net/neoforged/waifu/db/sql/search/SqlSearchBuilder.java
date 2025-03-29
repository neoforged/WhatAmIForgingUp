package net.neoforged.waifu.db.sql.search;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CanIgnoreReturnValue
public final class SqlSearchBuilder {
    private final String table;
    final Set<String> columns = new LinkedHashSet<>();
    private final Set<String> groups = new LinkedHashSet<>();
    private final List<SqlCondition> conditions = new ArrayList<>();
    private final SqlArgumentContext ctx = new SqlArgumentContext();
    private final Multimap<String, SqlCondition> joins = Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);

    private String orderColumn;

    private int limit;

    public SqlSearchBuilder(String table) {
        this.table = table;
    }

    public SqlSearchBuilder requestColumn(String col) {
        columns.add(col);
        return this;
    }

    public SqlSearchBuilder where(String column, SqlFilter filter) {
        conditions.add(SqlCondition.columnFilter(filter, column));
        return this;
    }

    public SqlSearchBuilder where(SqlCondition clause) {
        conditions.add(clause);
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

    public SqlSearchBuilder joinOn(String column, SqlCondition cond) {
        joins.put(column, cond);
        return this;
    }

    public SqlSearchBuilder joinOn(String column, Iterable<SqlCondition> filters) {
        joins.putAll(column, filters);
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
                .append(filters(fil)));

        if (!conditions.isEmpty()) {
            builder.append(" where ")
                    .append(conditions.stream()
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
            var arg = ctx.args.get(i);
            if (arg instanceof Object[] ar) {
                q.bindArray("i" + i, ar[0].getClass(), ar);
            } else if (arg instanceof List<?> lst) {
                q.bindArray("i" + i, lst.get(0).getClass(), lst);
            } else {
                q.bind("i" + i, arg);
            }
        }
        return q;
    }

    private String filters(Collection<SqlCondition> filters) {
        return filters.stream()
                .map(c -> c.build(ctx))
                .collect(Collectors.joining(" and "));
    }
}
