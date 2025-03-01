package net.neoforged.waifu;

import net.neoforged.waifu.platform.ModLoader;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.enums.EnumByName;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

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

    public void addGameVersion(String gameVersion, ModLoader loader, @Nullable Long intervalSeconds) {
        transactional.addGameVersion(gameVersion, loader, intervalSeconds);
    }

    public List<IndexVersion> getIndexedGameVersions() {
        return transactional.getIndexedGameVersions();
    }

    public boolean deleteVersion(String gameVersion, ModLoader loader) {
        return transactional.deleteVersion(gameVersion, loader) == 1;
    }

    private interface DBTrans extends Transactional<DBTrans> {
        @SqlUpdate("insert into indexed_game_versions(version, loader, index_interval) values (?, ?, ?)")
        void addGameVersion(String version, @EnumByName ModLoader loader, @Nullable Long intervalSeconds);

        @SqlUpdate("delete from indexed_game_versions where version = ? and loader = ?")
        int deleteVersion(String version, @EnumByName ModLoader loader);

        @UseRowMapper(IndexVersion.Mapper.class)
        @SqlQuery("select * from indexed_game_versions")
        List<IndexVersion> getIndexedGameVersions();
    }

    public record IndexVersion(String gameVersion, ModLoader loader, long indexInterval) {
        public static class Mapper implements RowMapper<IndexVersion> {
            @Override
            public IndexVersion map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new IndexVersion(rs.getString("version"), ModLoader.valueOf(rs.getString("loader")), rs.getLong("index_interval"));
            }
        }

        @Override
        public String toString() {
            return "`" + gameVersion + "` (" + loader.name().toLowerCase(Locale.ROOT) + ")";
        }
    }
}
