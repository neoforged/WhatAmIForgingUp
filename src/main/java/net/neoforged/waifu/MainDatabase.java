package net.neoforged.waifu;

import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainDatabase {
    private final SQLiteDataSource dataSource;
    private final DBTrans transactional;

    public MainDatabase(Path path) {
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

        var jdbi = Jdbi.create(dataSource);

        jdbi.installPlugin(new SqlObjectPlugin());

        this.transactional = jdbi.onDemand(DBTrans.class);
    }

    public void runFlyway() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:maindb/migration")
                .load()
                .migrate();
    }

    public void addGameVersion(String gameVersion) {
        transactional.addGameVersion(gameVersion);
    }

    public List<String> getIndexedGameVersions() {
        return transactional.getIndexedGameVersions();
    }

    private interface DBTrans extends Transactional<DBTrans> {
        @SqlUpdate("insert into indexed_game_versions(version) values (?)")
        void addGameVersion(String version);

        @SqlQuery("select version from indexed_game_versions")
        List<String> getIndexedGameVersions();
    }
}
