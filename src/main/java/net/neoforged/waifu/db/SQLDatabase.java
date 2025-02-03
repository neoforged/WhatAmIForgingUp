package net.neoforged.waifu.db;

import com.google.gson.JsonArray;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModInfo;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.ThrowingConsumer;
import net.neoforged.waifu.util.Utils;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.ConnectionFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.ResultProducer;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.sql.Array;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SQLDatabase implements IndexDatabase<SQLDatabase.SqlMod> {
    private final Jdbi jdbi;
    private final String url, username, password;
    private final ConnectionFactory connectionFactory;

    public SQLDatabase(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connectionFactory = () -> DriverManager.getConnection(url, username, password);
        this.jdbi = Jdbi.create(connectionFactory);

        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.installPlugin(new PostgresPlugin());
    }

    public void runFlyway() {
        Flyway.configure()
                .locations("classpath:indexdb/migration")
                .dataSource(url, username, password)
                .createSchemas(true)
                .load()
                .migrate();

        if (Utils.VERSION != null) {
            jdbi.useHandle(handle -> handle.createUpdate("insert into waifu_versions(version, date_installed) values (?, ?) on conflict do nothing")
                    .bind(0, Utils.VERSION)
                    .bind(1, Timestamp.from(Instant.now()))
                    .execute());
        }
    }

    // TODO - reduce duplication?

    @Override
    public @Nullable SQLDatabase.SqlMod getMod(PlatformModFile file) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where " + file.getPlatform().getName() + "_project_id = ?")
                        .bind(0, file.getModId())
                        .execute(returning(SqlMod::new)));
    }

    @Override
    public @Nullable SQLDatabase.SqlMod getMod(String coords) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where maven_coordinates = ?")
                        .bind(0, coords)
                        .execute(returning(SqlMod::new)));
    }

    @Override
    public SqlMod createMod(ModFileInfo modInfo) {
        var mod = jdbi.withHandle(handle ->
                handle.createUpdate("insert into mods(version, name, mod_ids) values (?, ?, ?) returning *")
                        .bind(0, modInfo.getVersion().toString())
                        .bind(1, modInfo.getDisplayName())
                        .bind(2, modInfo.getMods().stream().map(ModInfo::modId).toArray(String[]::new))
                        .execute(returning(SqlMod::new)));
        mod.updateMetadata(modInfo);
        return mod;
    }

    @Override
    public SqlMod getModByFileHash(String fileSha1) {
        return jdbi.withHandle(handle -> handle.createQuery("select mods.* from known_files join mods on mods.id = known_files.mod where known_files.sha1 = ?")
                .bind(0, fileSha1)
                .execute(returning(SqlMod::new)));
    }

    @Override
    public @Nullable SQLDatabase.SqlMod getLoaderMod(String coords) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where maven_coordinates = ? and loader = true")
                        .bind(0, coords)
                        .execute(returning(SqlMod::new)));
    }

    @Override
    public SqlMod createLoaderMod(ModFileInfo modInfo) {
        var mod = jdbi.withHandle(handle ->
                handle.createUpdate("insert into mods(version, name, mod_ids, loader) values (?, ?, ?, true) returning *")
                        .bind(0, modInfo.getVersion().toString())
                        .bind(1, modInfo.getDisplayName())
                        .bind(2, modInfo.getMods().stream().map(ModInfo::modId).toArray(String[]::new))
                        .execute(returning(SqlMod::new)));
        mod.updateMetadata(modInfo);
        return mod;
    }

    @Override
    public @Nullable Instant getKnownLatestProjectFileDate(PlatformModFile file) {
        return jdbi.withHandle(handle -> {
            var query = handle.createQuery("select id, latest_date from known_" + file.getPlatform().getName() + "_file_ids where id = ?");
            if (file.getId().getClass() == String.class) {
                query.bind(0, (String) file.getId());
            } else {
                query.bind(0, (Integer) file.getId());
            }
            return query.execute((statementSupplier, ctx) -> {
                var rs = statementSupplier.get();
                if (rs.getResultSet().next()) {
                    return rs.getResultSet().getTimestamp("latest_date").toInstant();
                }
                return null;
            });
        });
    }

    @Override
    public void markKnownById(PlatformModFile file, Instant latestDate) {
        jdbi.useHandle(handle -> {
            var update = handle.createUpdate("insert into known_" + file.getPlatform().getName() + "_file_ids(id, latest_date) values (?, ?) on conflict do nothing");
            if (file.getId().getClass() == String.class) {
                update.bind(0, (String) file.getId());
            } else {
                update.bind(0, (Integer) file.getId());
            }
            update.bind(1, Date.from(latestDate));
            update.execute();
        });
    }

    @Override
    public <E extends Exception> void trackMod(SqlMod mod, ThrowingConsumer<ModTracker, E> consumer) throws E {
        var modId = mod.id;
        try (var con = connectionFactory.openConnection()) {
            consumer.accept(new ModTracker() {
                @Override
                public void insertClasses(List<ClassData> classes) {
                    if (classes.isEmpty()) return;

                    try {
                        var stmt = new BatchingStatement(con.prepareStatement("select * from insert_class(?, ?, ?, ?, ?, ?, ?, ?)"), 500);
                        for (var aClass : classes) {
                            stmt.setInt(1, modId);
                            stmt.setString(2, aClass.name());
                            stmt.setString(3, aClass.superClass());
                            stmt.setArray(4, con.createArrayOf("text", aClass.interfaces()));
                            stmt.setString(5, Utils.GSON.toJson(formatAnnotations(aClass.annotations())));
                            stmt.setString(6, fields(aClass));
                            stmt.setString(7, methods(aClass));
                            stmt.setString(8, refs(aClass));
                            stmt.addBatch();
                        }

                        stmt.executeBatch();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void insertTags(List<TagFile> tags) {
                    if (tags.isEmpty()) return;

                    try {
                        var stmt = new BatchingStatement(con.prepareStatement("select * from insert_tag(?, ?, ?, ?)"), 250);
                        for (var tag : tags) {
                            stmt.setInt(1, modId);
                            stmt.setString(2, tag.name());
                            stmt.setBoolean(3, tag.replace());
                            stmt.setArray(4, con.createArrayOf("text", tag.entries().toArray(String[]::new)));
                            stmt.addBatch();
                        }

                        stmt.executeBatch();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void deleteCurrent() {
                    try {
                        {
                            var stmt = con.prepareStatement("delete from class_defs where mod = ?");
                            stmt.setInt(1, modId);
                            stmt.execute();
                        }
                        {
                            var stmt = con.prepareStatement("delete from tags where mod = ?");
                            stmt.setInt(1, modId);
                            stmt.execute();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void markAsKnown(String fileSha1) {
                    try {
                        var stmt = con.prepareStatement("insert into known_files(mod, sha1) values (?, ?)");
                        stmt.setInt(1, modId);
                        stmt.setString(2, fileSha1);
                        stmt.execute();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void setIndexDate(Instant date) {
                    try {
                        var stmt = con.prepareStatement("update mods set index_date = ? where id = ?");
                        stmt.setTimestamp(1, Timestamp.from(date));
                        stmt.setInt(2, modId);
                        stmt.execute();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
    }

    private static <T> ResultProducer<T> returning(Mapper<T> function) {
        return (statementSupplier, ctx) -> {
            var res = statementSupplier.get().getResultSet();
            if (res.next()) return function.apply(res);
            return null;
        };
    }

    @FunctionalInterface
    private interface Mapper<T> {
        T apply(ResultSet rs) throws SQLException;
    }

    private static String methods(ClassData cd) {
        var json = new JsonArray();

        for (ClassData.MethodInfo method : cd.methods().values()) {
            var sub = new JsonArray();
            sub.add(method.name());
            sub.add(method.desc());
            if (!method.annotations().isEmpty()) {
                sub.add(formatAnnotations(method.annotations()));
            }
            json.add(sub);
        }

        return Utils.GSON.toJson(json);
    }

    private static String refs(ClassData cd) {
        var json = new JsonArray();

        {
            var subs = new JsonArray();
            cd.methodRefs().forEach((reference, cnt) -> {
                var subSub = new JsonArray();
                subSub.add(reference.owner());
                subSub.add(reference.name());
                subSub.add(reference.desc());
                subSub.add(cnt);
                subs.add(subSub);
            });
            json.add(subs);
        }

        {
            var subs = new JsonArray();
            cd.fieldRefs().forEach((reference, cnt) -> {
                var subSub = new JsonArray();
                subSub.add(reference.owner());
                subSub.add(reference.name());
                subSub.add(reference.desc());
                subSub.add(cnt);
                subs.add(subSub);
            });
            json.add(subs);
        }

        return Utils.GSON.toJson(json);
    }

    private static String fields(ClassData cd) {
        var json = new JsonArray();

        for (var fields : cd.fields().values()) {
            var sub = new JsonArray();
            sub.add(fields.name());
            sub.add(fields.desc().getInternalName());
            if (!fields.annotations().isEmpty()) {
                sub.add(formatAnnotations(fields.annotations()));
            }
            json.add(sub);
        }

        return Utils.GSON.toJson(json);
    }

    @SuppressWarnings("DuplicatedCode")
    private static JsonArray formatAnnotations(List<ClassData.AnnotationInfo> anns) {
        var json = new JsonArray();
        if (anns.isEmpty()) {
            return json;
        }

        for (ClassData.AnnotationInfo ann : anns) {
            var js = new JsonArray();
            js.add(ann.type().getInternalName());
            var builder = new StringBuilder();
            var itr = ann.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).iterator();
            while (itr.hasNext()) {
                var next = itr.next();
                builder.append(next.getKey()).append("=");
                appendMember(builder, next.getValue());
                if (itr.hasNext()) builder.append(',');
            }
            js.add(builder.toString().replace("\u0000", "")); // Catch weird escapes for weird annotations like kotlin's (but in case we can't catch it)

            json.add(js);
        }
        return json;
    }

    @SuppressWarnings("DuplicatedCode")
    private static void appendMember(StringBuilder builder, Object member) {
        switch (member) {
            case ClassData.AnnotationInfo ai -> {
                builder.append("@").append(ai.type().getInternalName())
                        .append("(");
                var itr = ai.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).iterator();
                while (itr.hasNext()) {
                    var next = itr.next();
                    builder.append(next.getKey()).append("=");
                    appendMember(builder, next.getValue());
                    if (itr.hasNext()) builder.append(',');
                }
                builder.append(")");
            }
            case List<?> list -> {
                builder.append("[");
                var itr = list.iterator();
                while (itr.hasNext()) {
                    appendMember(builder, itr.next());
                    if (itr.hasNext()) builder.append(',');
                }
                builder.append(']');
            }
            case String str -> builder.append('"').append(str).append("'");
            case Type tp -> builder.append(tp.getInternalName()).append(".class");
            default -> builder.append(member);
        }
    }

    public class SqlMod implements DatabaseMod<SqlMod> {
        private final int id;
        private final String mavenCoordinates;
        private final String version;
        private final boolean loader;

        public SqlMod(ResultSet rs) throws SQLException {
            this.id = rs.getInt("id");
            this.mavenCoordinates = rs.getString("maven_coordinates");
            this.version = rs.getString("version");
            this.loader = rs.getBoolean("loader");
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public @Nullable String getMavenCoordinate() {
            return mavenCoordinates;
        }

        @Override
        public boolean isLoader() {
            return loader;
        }

        @Override
        public void updateMetadata(ModFileInfo info) {
            var meta = info.getMetadata();

            String mtoml = null;
            try {
                mtoml = Files.readString(info.getPath("META-INF/neoforge.mods.toml"));
            } catch (Exception ignored) {

            }

            String modsToml = mtoml;

            // TODO - find a better way that retains old data in case we update from a JiJ artifact that's also linked to a project
            jdbi.useHandle(handle -> handle.createUpdate("update mods set version = ?, name = ?, mod_ids = ?, authors = ?, update_json = ?, nested_tree = ?, maven_coordinates = ?, license = ?, language_loader = ?, mods_toml = ? where id = ?")
                    .bind(0, info.getVersion().toString())
                    .bind(1, info.getDisplayName())
                    .bind(2, info.getMods().stream().map(ModInfo::modId).toArray(String[]::new))
                    .bind(3, orNull(info.getMods().stream().map(ModInfo::authors)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("; "))))
                    .bind(4, info.getMods().isEmpty() ? null : info.getMods().get(0).updateJSONURL())
                    .bind(5, orNull(getNestedTree(info)))
                    .bind(6, info.getMavenCoordinates() == null ? mavenCoordinates : info.getMavenCoordinates())
                    .bind(7, meta == null ? null : meta.license())
                    .bind(8, meta == null ? null : meta.languageLoader())
                    .bind(9, modsToml)
                    .bind(10, id)
                    .execute());
        }

        @Override
        public void link(PlatformModFile platformFile) {
            jdbi.useHandle(handle -> handle.createUpdate("update mods set " + platformFile.getPlatform().getName() + "_project_id = ? where id = ?")
                    .bind(0, platformFile.getModId())
                    .bind(1, id)
                    .execute());
        }

        @Override
        public void link(String mavenCoords) {
            jdbi.useHandle(handle -> handle.createUpdate("update mods set maven_coordinates = ? where id = ?")
                    .bind(0, mavenCoords)
                    .bind(1, id)
                    .execute());
        }

        @Override
        public void transferTo(SqlMod other) {
            jdbi.useHandle(handle -> handle.createUpdate("update known_files set mod = ? where mod = ?")
                    .bind(0, other.id)
                    .bind(1, this.id)
                    .execute());
        }

        @Override
        public void delete() {
            jdbi.useHandle(handle -> handle.createUpdate("delete from mods where id = ?")
                    .bind(0, id)
                    .execute());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SqlMod other && other.id == this.id;
        }
    }

    private static String getNestedTree(ModFileInfo info) {
        return info.getNestedJars().stream()
                .map(jar -> jar.identifier() + (jar.info().getNestedJars().isEmpty() ? "" : " (" + getNestedTree(jar.info()) + ")"))
                .collect(Collectors.joining(", "));
    }

    private static String orNull(String str) {
        return str.isBlank() ? null : str;
    }

    private static class BatchingStatement {
        private final PreparedStatement statement;
        private final int batchSize;

        private int currentSize;

        private BatchingStatement(PreparedStatement statement, int batchSize) {
            this.statement = statement;
            this.batchSize = batchSize;
        }

        public void setString(int pos, String arg) throws SQLException {
            statement.setString(pos, arg);
        }

        public void setBoolean(int pos, boolean arg) throws SQLException {
            statement.setBoolean(pos, arg);
        }

        public void setArray(int pos, Array array) throws SQLException {
            statement.setArray(pos, array);
        }

        public void setInt(int pos, int arg) throws SQLException {
            statement.setInt(pos, arg);
        }

        public void addBatch() throws SQLException {
            statement.addBatch();
            currentSize++;
            if (currentSize == batchSize) {
                executeBatch();
            }
        }

        public void executeBatch() throws SQLException {
            if (currentSize > 0) {
                statement.executeBatch();
                currentSize = 0;
            }
        }
    }
}
