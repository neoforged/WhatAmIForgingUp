package net.neoforged.waifu.db.sql.search;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CanIgnoreReturnValue
public final class SqlSearchBuilder {
    private final String table;
    final Map<String, String> columns = new LinkedHashMap<>();
    private final Set<String> groups = new LinkedHashSet<>();
    final List<SqlCondition> where = new ArrayList<>();
    private final List<SqlCondition> having = new ArrayList<>();
    private final SqlArgumentContext ctx;
    private final Multimap<String, SqlCondition> joins = Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);

    private boolean requestAsJson;

    private String orderColumn;

    final Map<String, String> lowercasedAliases = new HashMap<>();

    private int limit;

    public SqlSearchBuilder(String table) {
        this(table, new SqlArgumentContext());
    }

    public SqlSearchBuilder(String table, SqlArgumentContext ctx) {
        this.table = table;
        this.ctx = ctx;
    }

    public SqlSearchBuilder requestColumn(String col, @Nullable String alias) {
        columns.put(alias, col);
        if (alias != null) {
            lowercasedAliases.put(alias.toLowerCase(Locale.ROOT), alias);
        }
        return this;
    }

    public SqlSearchBuilder requestSubColumn(String from, Consumer<SqlSearchBuilder> cons, String alias) {
        var sub = subBuilder(from);
        cons.accept(sub);
        return requestColumn("(" + sub.format() + ")", alias);
    }

    public SqlSearchBuilder where(String column, SqlFilter filter) {
        where.add(SqlCondition.columnFilter(filter, column));
        return this;
    }

    public SqlSearchBuilder where(SqlCondition clause) {
        where.add(clause);
        return this;
    }

    public SqlSearchBuilder having(SqlCondition clause) {
        having.add(clause);
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

    public SqlSearchBuilder requestAsJson() {
        this.requestAsJson = true;
        return this;
    }

    public String insert(Object value) {
        return ctx.insert(value);
    }

    public String format() {
        StringBuilder builder = new StringBuilder("select ");

        if (requestAsJson) {
            builder.append("jsonb_build_object(");
            builder.append(columns.entrySet().stream()
                    .map(e -> insert(e.getKey()) + ", " + e.getValue())
                    .collect(Collectors.joining(", ")));
            builder.append(") as json_out");
        } else {
            builder.append(columns.entrySet().stream()
                    .map(e -> e.getKey() == null ? e.getValue() : (e.getValue() + " as \"" + e.getKey() + "\""))
                    .collect(Collectors.joining(", ")));
        }

        builder.append(" from ")
                .append(table);

        var joins = this.joins.asMap();
        joins.forEach((tb, fil) -> builder.append(" join ")
                .append(tb)
                .append(" on ")
                .append(filters(fil)));

        if (!where.isEmpty()) {
            builder.append(" where ")
                    .append(where.stream()
                            .map(c -> c.build(ctx))
                            .collect(Collectors.joining(" and ")));
        }

        if (!groups.isEmpty()) {
            builder.append(" group by ")
                    .append(String.join(", ", groups));
        }

        if (!having.isEmpty()) {
            builder.append(" having ")
                    .append(having.stream()
                            .map(c -> c.build(ctx))
                            .collect(Collectors.joining(" and ")));
        }

        if (orderColumn != null) {
            builder.append(" order by ").append(orderColumn);
        }

        if (limit > 0) {
            builder.append(" limit ").append(limit);
        }
        return builder.toString();
    }

    public Query build(Handle handle) {
        var q = handle.createQuery(format());
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

    public SqlSearchBuilder subBuilder(String col) {
        return new SqlSearchBuilder(col, ctx);
    }

    public SqlSearchBuilder columnSubQuery(String column, String alias, Consumer<SqlSearchBuilder> sub) {
        var s = new SqlSearchBuilder(column, ctx);
        sub.accept(s);
        requestColumn("(" + s.format() + ")", alias);
        return this;
    }

    private String filters(Collection<SqlCondition> filters) {
        return filters.stream()
                .map(c -> c.build(ctx))
                .collect(Collectors.joining(" and "));
    }
}
