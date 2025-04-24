package net.neoforged.waifu.db.sql.search;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

public class DatabaseSchema {
    private final Map<String, DatabaseType> types = new HashMap<>();
    private final MutableGraph<String> joinPaths = GraphBuilder.directed().allowsSelfLoops(false).build();
    private final Map<JoinKey, SqlCondition> joinRules = new HashMap<>();

    public DatabaseType registerType(String table, Consumer<DatabaseType.Builder> cons) {
        var b = new DatabaseType.Builder(this, table);
        cons.accept(b);
        var type = b.build();
        types.put(table, type);
        return type;
    }

    public void registerJoinRule(String a, String b, SqlCondition joinCondition) {
        joinPaths.putEdge(a, b);
        joinPaths.putEdge(b, a);
        joinRules.put(JoinKey.create(a, b), joinCondition);
    }

    public void join(SqlSearchBuilder builder, String from, String to) {
        builder.joinOn(to, getJoinRule(from, to));
    }

    public SqlCondition getJoinRule(String from, String to) {
        return joinRules.get(JoinKey.create(from, to));
    }

    public DatabaseType getType(String name) {
        return types.get(name);
    }

    public void possiblyJoin(SqlSearchBuilder builder, String target) {
        var path = this.findShortestJoinPath(builder.table, target);
        if (path != null) {
            var last = builder.table;
            for (int i = 1; i < path.size(); i++) {
                var to = path.get(i);
                join(builder, last, to);
                last = to;
            }
        }
    }

    @Nullable
    public List<String> findShortestJoinPath(String start, String target) {
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));

        Set<String> visited = new HashSet<>();
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            var last = path.get(path.size() - 1);

            if (last.equals(target)) {
                return path;
            }

            for (var neighbor : joinPaths.successors(last)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
        return null; // No path found
    }

    public record JoinKey(String first, String second) {
        public static JoinKey create(String a, String b) {
            if (a.compareTo(b) < 0) return new JoinKey(a, b);
            return new JoinKey(b, a);
        }
    }
}
