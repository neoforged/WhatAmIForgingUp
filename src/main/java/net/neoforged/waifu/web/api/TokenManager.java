package net.neoforged.waifu.web.api;

import net.neoforged.waifu.util.DateUtils;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

import javax.swing.tree.TreePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    public String createToken(String name, @Nullable String limit) {
        String token = generate();

        transactional.insert(name, token, limit);

        onAdd.forEach(addCallback -> addCallback.onAdd(new Token(name, token, limit == null ? null : RateLimit.parse(limit))));

        return token;
    }

    @Nullable
    public String regenerate(String name) {
        var tok = transactional.getToken(name);
        if (tok == null) return null;
        removeToken(name);
        return createToken(name, tok.limit() == null ? null : tok.limit().toMachine());
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
        var tok = transactional.getToken(name);
        if (tok == null) return false;
        transactional.remove(name);
        onRemove.forEach(c -> c.onRemove(tok.token()));
        return true;
    }

    public List<Token> getTokens() {
        return transactional.getTokens();
    }

    public interface DBTrans {
        @SqlUpdate("insert into tokens(name, token, ratelimit) values (?, ?, ?)")
        void insert(String name, String token, @Nullable String limit);

        @Nullable
        @UseRowMapper(Token.Mapper.class)
        @SqlQuery("select * from tokens where name = ?")
        Token getToken(String name);

        @SqlUpdate("delete from tokens where name = ?")
        void remove(String name);

        @SqlQuery("select * from tokens")
        @UseRowMapper(Token.Mapper.class)
        List<Token> getTokens();
    }

    public record Token(String name, String token, @Nullable RateLimit limit) {
        public static class Mapper implements RowMapper<Token> {
            @Override
            public Token map(ResultSet rs, StatementContext ctx) throws SQLException {
                var limit = rs.getString("ratelimit");
                return new Token(rs.getString("name"), rs.getString("token"), limit == null ? null : RateLimit.parse(limit));
            }
        }
    }

    public record RateLimit(int requests, Duration per) {
        public static RateLimit parse(String in) {
            var spl = in.split("/");
            return new RateLimit(Integer.parseInt(spl[0].trim()), DateUtils.getDurationFromInput(spl[1].trim()));
        }

        private String toMachine() {
            return requests + "/" + per.getSeconds() + "s";
        }

        @Override
        public String toString() {
            return requests + " / " + per.getSeconds() + " seconds";
        }
    }

    public interface AddCallback {
        void onAdd(Token token);
    }
    public interface RemoveCallback {
        void onRemove(String token);
    }
}
