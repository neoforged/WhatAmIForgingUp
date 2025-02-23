package net.neoforged.waifu.db;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModInfo;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.ThrowingConsumer;
import net.neoforged.waifu.util.Utils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.jdbi.v3.core.ConnectionFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.ResultProducer;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.sql.Array;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
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
                .callbacks(new Callback() {

                    @Override
                    public boolean supports(Event event, Context context) {
                        return event == Event.AFTER_EACH_MIGRATE;
                    }

                    @Override
                    public boolean canHandleInTransaction(Event event, Context context) {
                        return true;
                    }

                    @Override
                    public void handle(Event event, Context context) {
                        if (context.getMigrationInfo().getVersion().getMajor().intValue() == 7) {
                            try {
                                var byId = new HashMap<Integer, String>();
                                try (var stmt = context.getConnection().createStatement()) {
                                    var res = stmt.executeQuery("select mods.id, mods.mods_toml from mods where mods_toml is not null");
                                    while (res.next()) {
                                        byId.put(res.getInt(1), res.getString(2));
                                    }
                                }

                                if (byId.isEmpty()) return;
                                try (var batch = new BatchingStatement(context.getConnection().prepareStatement("update mods set mods_toml_json = (?::jsonb) where id = ?"), 250)) {
                                    for (var entry : byId.entrySet()) {
                                        try {
                                            batch.setString(1, Utils.tomlToJson(entry.getValue()));
                                            batch.setInt(2, entry.getKey());
                                            batch.addBatch();
                                        } catch (Exception ignored) {
                                            // Ignore invalid tomls
                                        }
                                    }

                                    batch.executeBatch();
                                }

                                Main.LOGGER.info("Migrated {} mods from text mods.toml to json mods.toml", byId.size());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    @Override
                    public String getCallbackName() {
                        return "migrate_mods_toml_v7";
                    }
                })
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
    public List<SqlMod> getMods(ModPlatform platform, List<Object> projectIds) {
        var pidType = projectIds.get(0).getClass();
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where " + platform.getName() + "_project_id = any(?::" + (pidType == String.class ? "text" : "int") + "[])")
                        .bindArray(0, pidType, projectIds)
                        .execute(returningListOf(SqlMod::new)));
    }

    @Override
    public @Nullable SQLDatabase.SqlMod getModByCoordinates(String coords) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where maven_coordinates = ?")
                        .bind(0, coords)
                        .execute(returning(SqlMod::new)));
    }

    @Override
    public List<SqlMod> getModsByName(String name) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from mods where name = ?")
                        .bind(0, name)
                        .execute(returningListOf(SqlMod::new)));
    }

    @Override
    public Multimap<String, SqlMod> getModsByNameAtLeast2() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
with bycount as (select count(mods.id), mods.name from mods group by mods.name order by count desc)
select mods.* from bycount
join mods on mods.name = bycount.name
where bycount.count >= 2
order by mods.name;""")
                        .execute(map(returningListOf(SqlMod::new), sqlMods -> {
                            var map = Multimaps.<String, SqlMod>newListMultimap(new HashMap<>(), () -> new ArrayList<>(2));
                            sqlMods.forEach(mod -> map.put(mod.getName(), mod));
                            return map;
                        })));
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
                public void insertEnumExtensions(List<EnumExtension> extensions) {
                    if (extensions.isEmpty()) return;

                    try {
                        var stmt = new BatchingStatement(con.prepareStatement("select * from insert_enum_extension(?, ?, ?, ?, ?)"), 100);
                        for (var ext : extensions) {
                            stmt.setInt(1, modId);
                            stmt.setString(2, ext.enumName());
                            stmt.setString(3, ext.name());
                            stmt.setString(4, ext.constructor());
                            stmt.setString(5, Utils.GSON.toJson(ext.parameters()));
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
                        {
                            var stmt = con.prepareStatement("delete from enum_extensions where mod = ?");
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

    private static <T> ResultProducer<List<T>> returningListOf(Mapper<T> function) {
        return (statementSupplier, ctx) -> {
            var res = statementSupplier.get().getResultSet();
            var lst = new ArrayList<T>();
            while (res.next()) lst.add(function.apply(res));
            return lst;
        };
    }

    private static <T, R> ResultProducer<R> map(ResultProducer<T> prod, Function<T, R> func) {
        return (statementSupplier, ctx) -> func.apply(prod.produce(statementSupplier, ctx));
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

            var obj = new JsonObject();
            ann.members().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(v -> obj.add(v.getKey(), toJson(v.getValue())));
            js.add(obj);

            json.add(js);
        }
        return json;
    }

    private static JsonElement toJson(Object member) {
        return switch (member) {
            case ClassData.AnnotationInfo ai -> {
                var o = new JsonObject();
                ai.members().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(v -> o.add(v.getKey(), toJson(v.getValue())));
                o.addProperty("_$tp", ai.type().getInternalName());
                yield o;
            }
            case List<?> list -> {
                var ar = new JsonArray(list.size());
                for (Object o : list) {
                    ar.add(toJson(o));
                }
                yield ar;
            }
            case String str -> new JsonPrimitive(str.replace("\u0000", "")); // Catch weird escapes for weird annotations like kotlin's (but in case we can't catch it)
            case Character c -> new JsonPrimitive(c == '\u0000' ? "" : String.valueOf(c));
            case Boolean b -> new JsonPrimitive(b);
            case Number n -> new JsonPrimitive(n);
            case Type tp -> new JsonPrimitive(tp.getInternalName());
            case ClassData.EnumValue ev -> {
                var o = new JsonObject();
                o.addProperty("enum", ev.enumType().getInternalName());
                o.addProperty("value", ev.value());
                yield o;
            }

            case byte[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case boolean[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case short[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case char[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case int[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case long[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case float[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }
            case double[] in -> {
                var ar = new JsonArray(in.length);
                for (var i : in) {
                    ar.add(i);
                }
                yield ar;
            }

            default -> null;
        };
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
            case String str -> builder.append('"').append(str).append('"');
            case Type tp -> builder.append(tp.getInternalName()).append(".class");
            default -> builder.append(member);
        }
    }

    public class SqlMod implements DatabaseMod<SqlMod> {
        private final int id;
        private final String mavenCoordinates;
        private final String version, name;
        private final boolean loader;

        private final int cfProjectId;
        private final String modrinthProjectId;

        public SqlMod(ResultSet rs) throws SQLException {
            this.id = rs.getInt("id");
            this.mavenCoordinates = rs.getString("maven_coordinates");
            this.version = rs.getString("version");
            this.name = rs.getString("name");
            this.loader = rs.getBoolean("loader");

            cfProjectId = rs.getInt("curseforge_project_id");
            modrinthProjectId = rs.getString("modrinth_project_id");
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public @Nullable String getMavenCoordinate() {
            return mavenCoordinates;
        }

        @Override
        public @Nullable Integer getCurseForgeProjectId() {
            return cfProjectId == 0 ? null : cfProjectId;
        }

        @Override
        public @Nullable String getModrinthProjectId() {
            return modrinthProjectId;
        }

        @Override
        public Map<String, Object> getPlatformIds() {
            var map = new HashMap<String, Object>();
            if (cfProjectId > 0) map.put(ModPlatform.CURSEFORGE, cfProjectId);
            if (modrinthProjectId != null) map.put(ModPlatform.MODRINTH, modrinthProjectId);
            return map;
        }

        @Override
        public boolean isLoader() {
            return loader;
        }

        @Override
        public void updateMetadata(ModFileInfo info) {
            var meta = info.getMetadata();

            String mtoml = null;
            String mtomlJson = null;
            try {
                var met = info.getModMetadata();
                if (met != null) {
                    mtoml = met.first();
                    mtomlJson = met.second();
                }
            } catch (Exception ignored) {

            }

            String modsToml = mtoml, modsTomlJson = mtomlJson;

            // TODO - find a better way that retains old data in case we update from a JiJ artifact that's also linked to a project
            jdbi.useHandle(handle -> handle.createUpdate("update mods set " +
                            "version = :ver, name = :name, mod_ids = :mids, authors = :authors," +
                            "nested_tree = (:nested::jsonb), maven_coordinates = :coords, license = :license," +
                            "mod_metadata = :meta, mod_metadata_json = (:metajson::jsonb), manifest = (:man::jsonb)" +
                            "where id = :id")
                    .bind("ver", info.getVersion().toString())
                    .bind("name", info.getDisplayName())
                    .bind("mids", info.getMods().stream().map(ModInfo::modId).toArray(String[]::new))
                    .bind("authors", orNull(info.getMods().stream().map(ModInfo::authors)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("; "))))
                    .bind("nested", nestedTree(info))
                    .bind("coords", info.getMavenCoordinates() == null ? mavenCoordinates : info.getMavenCoordinates())
                    .bind("license", meta == null ? null : meta.license())

                    .bind("meta", modsToml)
                    .bind("metajson", modsTomlJson)
                    .bind("man", Utils.GSON.toJson(manifestToJson(info.getManifest())))
                    .bind("id", id)
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

    @Nullable
    private static String nestedTree(ModFileInfo info) {
        if (info.getNestedJars().isEmpty()) return null;
        return Utils.GSON.toJson(computeNestedTree(info));
    }

    private static JsonArray computeNestedTree(ModFileInfo info) {
        var arr = new JsonArray();
        for (ModFileInfo.NestedJar nestedJar : info.getNestedJars()) {
            var json = new JsonObject();
            json.addProperty("id", nestedJar.identifier());
            json.addProperty("version", nestedJar.version().toString());
            var children = computeNestedTree(nestedJar.info());
            if (!children.isEmpty()) {
                json.add("nested", children);
            }
            arr.add(json);
        }
        return arr;
    }

    private static JsonObject manifestToJson(Manifest manifest) {
        var json = new JsonObject();
        var main = new JsonObject();
        manifest.getMainAttributes().forEach((key, val) -> {
            if (key instanceof Attributes.Name) {
                main.addProperty(key.toString(), val.toString());
            }
        });
        json.add("", main);

        manifest.getEntries().forEach((entryKey, entry) -> {
            var entryJson = new JsonObject();
            entry.forEach((key, val) -> {
                if (key instanceof Attributes.Name) {
                    entryJson.addProperty(key.toString(), val.toString());
                }
            });
            json.add(entryKey, entryJson);
        });

        return json;
    }

    private static String orNull(String str) {
        return str.isBlank() ? null : str;
    }

    private static class BatchingStatement implements AutoCloseable {
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

        @Override
        public void close() throws Exception {
            statement.close();
        }
    }
}
