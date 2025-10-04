package net.neoforged.waifu.web.api;

import net.neoforged.waifu.util.DateUtils;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class TokenManager {
    private final SQLiteDataSource dataSource;
    private final DBTrans transactional;

    private final List<AddCallback> onAdd = new ArrayList<>();
    private final List<RemoveCallback> onRemove = new ArrayList<>();

    public TokenManager(Path path) {
        if (!Files.exists(path)) {
            try {
                var parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.createFile(path);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        final String url = "jdbc:sqlite:" + path.toAbsolutePath();
        this.dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        dataSource.setEncoding("UTF-8");
        dataSource.setDatabaseName("WAIFU main");
        dataSource.setEnforceForeignKeys(true);
        runFlyway();

        var jdbi = Jdbi.create(dataSource);

        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.registerColumnMapper(RateLimit.class, new RateLimit.Mapper());
        jdbi.registerRowMapper(Token.class, ConstructorMapper.of(Token.class));

        this.transactional = jdbi.onDemand(DBTrans.class);
    }

    private void runFlyway() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/token/migration")
                .load()
                .migrate();
    }

    public void addAddCallback(AddCallback cb) {
        onAdd.add(cb);
    }

    public void addRemoveCallback(RemoveCallback cb) {
        onRemove.add(cb);
    }

    public Token createToken(String name) {
        var token = transactional.create(name);
        onAdd.forEach(addCallback -> addCallback.onAdd(token));
        return token;
    }

    @Nullable
    public Token getToken(String name) {
        return transactional.get(name);
    }

    public Token regenerate(String name) {
        return update(name).setToken(generate()).execute();
    }

    private String generate() {
        Random random = new Random();
        return random.ints('0', 'z' + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(64)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public boolean removeToken(String name) {
        var tok = transactional.get(name);
        if (tok == null) return false;
        transactional.delete(name);
        onRemove.forEach(c -> c.onRemove(tok.token()));
        return true;
    }

    private Token triggerUpdate(Token token) {
        onRemove.forEach(c -> c.onRemove(token.name()));
        onAdd.forEach(c -> c.onAdd(token));
        return token;
    }

    public TokenUpdater update(Token token) {
        return update(token.name());
    }

    public TokenUpdater update(String name) {
        return new TokenUpdater(name);
    }

    public List<Token> getTokens() {
        return transactional.getTokens();
    }

    public interface DBTrans extends Transactional<DBTrans> {
        @SqlQuery("insert into tokens(name, active) values (?, false) returning *")
        Token create(String name);

        @Nullable
        @SqlQuery("select * from tokens where name = ?")
        Token get(String name);

        @SqlUpdate("delete from tokens where name = ?")
        void delete(String name);

        @SqlQuery("select * from tokens")
        List<Token> getTokens();
    }

    public class TokenUpdater {
        private final String name;

        private final Map<String, Object> updates = new LinkedHashMap<>();

        private TokenUpdater(String name) {
            this.name = name;
        }

        public TokenUpdater setToken(String token) {
            updates.put("token", token);
            updates.put("token_last_generated", Instant.now());
            return this;
        }

        public TokenUpdater setActive(boolean active) {
            updates.put("active", active);
            return this;
        }

        public TokenUpdater setRateLimit(@Nullable RateLimit rateLimit) {
            updates.put("ratelimit", rateLimit == null ? null : rateLimit.toMachine());
            return this;
        }

        public TokenUpdater setTimeout(@Nullable Integer timeout) {
            updates.put("timeout", timeout);
            return this;
        }

        public Token execute() {
            return transactional.withHandle(handle -> {
                var query = handle.createQuery("update tokens set " + updates.keySet()
                        .stream().map(o -> o + " = ?")
                        .collect(Collectors.joining(", ")) + " where name = ? returning *");
                int pos = 0;
                for (Object value : updates.values()) {
                    query.bind(pos++, value);
                }

                query.bind(pos, name);

                return triggerUpdate(query.mapTo(Token.class).one());
            });
        }
    }

    public record Token(
            String name, boolean active,
            @Nullable String token, @ColumnName("token_last_generated") @Nullable Instant tokenLastGenerated,
            @ColumnName("ratelimit") @Nullable RateLimit limit,
            @ColumnName("timeout") @Nullable Integer executionTimeout
    ) {
    }

    public record RateLimit(int requests, Duration per) {
        public static class Mapper implements ColumnMapper<RateLimit> {
            @Override
            public RateLimit map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
                var value = r.getString(columnNumber);
                return value == null ? null : parse(value);
            }
        }

        public static RateLimit parse(String in) {
            var spl = in.split("/");
            return new RateLimit(Integer.parseInt(spl[0].trim()), DateUtils.getDurationFromInput(spl[1].trim()));
        }

        public String toMachine() {
            return requests + "/" + per.getSeconds() + "s";
        }

        @Override
        public String toString() {
            return requests + " requests / " + DateUtils.formatDuration(per);
        }
    }

    public interface AddCallback {
        void onAdd(Token token);
    }
    public interface RemoveCallback {
        void onRemove(String token);
    }
}
