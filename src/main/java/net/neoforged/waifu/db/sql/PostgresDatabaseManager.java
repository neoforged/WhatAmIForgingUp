package net.neoforged.waifu.db.sql;

import net.neoforged.waifu.db.DatabaseManager;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.ConnectionFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

public class PostgresDatabaseManager implements DatabaseManager {
    private final String url;
    private final Properties baseProperties;

    private final ConnectionFactory baseConnection;

    public PostgresDatabaseManager(String url, String username, String password) {
        this.url = url;

        baseProperties = new Properties();
        baseProperties.put("user", username);
        baseProperties.put("password", password);
        baseProperties.put("loginTimeout", 20);

        System.getenv().forEach((key, val) -> {
            if (key.startsWith("POSTGRES_OPT_")) {
                baseProperties.put(key.replace("POSTGRES_OPT_", ""), val);
            }
        });

        var base = copy();
        this.baseConnection = () -> DriverManager.getConnection(url, base);
    }

    @Override
    public IndexDatabase<?> getDatabase(String gameVersion, ModLoader loader) {
        var props = copy();
        props.setProperty("currentSchema", schema(gameVersion, loader));

        var db = new SQLDatabase(this, () -> DriverManager.getConnection(url, props), loader);

        var source = new PGSimpleDataSource();
        source.setUrl(url);
        baseProperties.forEach((k, v) -> {
            try {
                source.setProperty((String) k, (String) v);
            } catch (SQLException e) {
                Utils.sneakyThrow(e);
            }
        });
        source.setCurrentSchema(schema(gameVersion, loader));

        db.runFlyway(source);

        return db;
    }

    @Override
    public DatabaseSearchHelper search(String gameVersion, ModLoader loader) {
        var props = copy();
        props.put("readOnly", "true");
        props.put("currentSchema", schema(gameVersion, loader));
        return new SQLSearchHelper(jdbi(props), loader);
    }

    @Override
    public boolean exists(String gameVersion, ModLoader loader) {
        try (var con = baseConnection.openConnection()) {
            var stmt = con.prepareStatement("select nspname from pg_catalog.pg_namespace where nspname = ?");
            stmt.setString(1, schema(gameVersion, loader));
            stmt.execute();
            return stmt.getResultSet().next();
        } catch (Exception ex) {
            return false;
        }
    }

    private Properties copy() {
        return new Properties(baseProperties);
    }

    private Jdbi jdbi(Properties props) {
        return getJdbi(() -> DriverManager.getConnection(url, props));
    }

    Jdbi getJdbi(ConnectionFactory factory) {
        var jdbi = Jdbi.create(factory);
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.installPlugin(new PostgresPlugin());
        return jdbi;
    }

    private String schema(String version, ModLoader loader) {
        return version + "-" + loader.name().toLowerCase(Locale.ROOT);
    }
}
