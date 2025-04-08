package net.neoforged.waifu.db.sql.search;

import com.google.common.collect.ImmutableBiMap;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.HashPrefixSqlParser;
import org.jdbi.v3.core.statement.SqlStatements;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class SqlSearchHelper implements DatabaseSearchHelper {
    public static final Map<String, FilterCriterion> MANIFEST_CRITERIA = Map.of(
            "name", FilterCriterion.jsonExpression("mods.manifest", "$.*[*].key"),
            "value", FilterCriterion.jsonExpression("mods.manifest", "$.*[*].value")
    );

    @SuppressWarnings("unchecked")
    private static final Map<String, FilterCriterion> GENERAL_MOD_CRITERIA = Map.of(
            "name", FilterCriterion.column("mods.name"),
            "authors", FilterCriterion.column("mods.authors"),
            "license", FilterCriterion.column("mods.license"),

            "anyClassName", FilterCriterion.column("classes.name"),

            "inPack", new InPackCriterion("curseforge_project_id", "modrinth_project_id"),
            "curseforgeProjectId", FilterCriterion.column("curseforge_project_id"),
            "modrinthProjectId", FilterCriterion.column("modrinth_project_id"),

            "anyManifestAttribute", val -> SqlCondition.parseAsCriterion((Map<String, Object>) val, MANIFEST_CRITERIA),

            "indexed", val -> SqlCondition.columnFilter(SqlFilter.parseDateTime((Map<String, Object>) val), "index_date")
    );

    private static final Map<String, FilterCriterion> FORGE_MOD_CRITERIA = ImmutableBiMap.<String, FilterCriterion>builder()
            .putAll(GENERAL_MOD_CRITERIA)
            .put("modId", FilterCriterion.jsonExpression("mods.mod_metadata_json", "$.mods[*].modId"))
            .put("description", FilterCriterion.jsonExpression("mods.mod_metadata_json", "$.mods[*].description"))
            .build();

    private static final Map<String, FilterCriterion> FABRIC_MOD_CRITERIA = ImmutableBiMap.<String, FilterCriterion>builder()
            .putAll(GENERAL_MOD_CRITERIA)
            .put("modId", FilterCriterion.jsonExpression("mods.mod_metadata_json", "$.id"))
            .put("description", FilterCriterion.jsonExpression("mods.mod_metadata_json", "$.description"))
            .build();

    private static final Map<String, FilterCriterion> CLASS_CRITERIA = Map.of(
            "name", FilterCriterion.column("classes.name")
    );

    private static final Map<String, FilterCriterion> TAG_CRITERIA = Map.of(
            "name", FilterCriterion.column("tagname"),
            "replace", FilterCriterion.column("tags.replace"),
            "anyEntry", FilterCriterion.column("entryname.constant")
    );

    private static final Map<String, String> MOD_FIELD_MAPPING = Map.of(
            "name", "name",
            "authors", "authors",
            "license", "license",
            "id", "id",
            "version", "version",
            "curseforgeProjectId", "curseforge_project_id",
            "modrinthProjectId", "modrinth_project_id",
            "mavenCoordinates", "maven_coordinates",
            "manifest", "manifest",
            "indexedOn", "index_date"
    );

    private static final int MAX_ITEMS_PER_REQUEST = 500;

    private final Jdbi jdbi;
    private final ModLoader loader;

    public SqlSearchHelper(Jdbi jdbi, ModLoader loader) {
        this.jdbi = jdbi;
        this.loader = loader;
        jdbi.getConfig(SqlStatements.class).setSqlParser(new HashPrefixSqlParser());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getModsById(DataFetchingEnvironment env) {
        var builder = new SqlSearchBuilder("mods");

        var ids = new ArrayList<>((List<Integer>) env.getArgument("ids"));

        builder.where(ctx -> "mods.id = any(" + ctx.insert(ids) + ")");

        var mods = env.getSelectionSet().getFields("mods");
        configureModSearch(mods.isEmpty() ? EmptySelectionSet.INSTANCE : mods.getFirst().getSelectionSet(), builder, false);

        return paginate(builder, Pagination.parse(env.getArguments()), "mods.id");
    }

    @Override
    public Object getMods(DataFetchingEnvironment env) {
        var builder = new SqlSearchBuilder("mods");

        boolean requireClassJoin = false;

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            var applied = new HashSet<String>();
            builder.where(SqlCondition.parseAsCriterion(filter, loader == ModLoader.FABRIC ? FABRIC_MOD_CRITERIA : FORGE_MOD_CRITERIA, applied));

            if (applied.contains("anyClassName")) {
                requireClassJoin = true;
            }
        }

        var mods = env.getSelectionSet().getFields("edges/node");
        configureModSearch(mods.isEmpty() ? EmptySelectionSet.INSTANCE : mods.getFirst().getSelectionSet(), builder, requireClassJoin);

        return paginate(builder, Pagination.parse(env.getArguments()), "mods.id");
    }

    @SuppressWarnings("unchecked")
    private void configureModSearch(DataFetchingFieldSelectionSet set, SqlSearchBuilder builder, boolean requireClassJoin) {
        builder.requestColumn("mods.id", "id"); // We always request the ID for pagination purposes

        MOD_FIELD_MAPPING.forEach((fld, dbMapping) -> {
            if (set.contains(fld)) {
                builder.requestColumn("mods." + dbMapping, fld);
            }
        });

        List<SqlCondition> classJoinFilter = new ArrayList<>();
        if (set.contains("classes")) {
            requireClassJoin = true;
            var selection = set.getFields("classes").getFirst();
            var filArgs = selection.getArguments().get("where");
            if (filArgs != null) {
                classJoinFilter.add(SqlCondition.parseAsCriterion((Map<String, Object>) filArgs, CLASS_CRITERIA, new HashSet<>()));
            }

            String aggIn = "classes.name";

            var order = (Map<String, Map<String, Object>>) selection.getArguments().get("order");
            if (order != null) {
                var name = order.get("name");
                if (name != null) {
                    aggIn = aggIn + " order by " + new Order(name).createStatement(aggIn);
                }
            }

            var limitText = "";
            var limit = (Integer) selection.getArguments().get("limit");
            if (limit != null) {
                limitText = "[1:" + limit + "]";
            }

            builder.requestColumn("(array_agg(" + aggIn + ")::text[])" + limitText, "classes");
        }

        set.getFields("tags").forEach(field -> {
            var reg = (String) field.getArguments().get("registry");

            String baseTag = "/" + reg.replace(':', '/').replace("minecraft/", "") + "/";

            var tagReplace = builder.insert(baseTag);

            var sub = builder.subBuilder("tags")
                    .requestAsJson()
                    .joinOn("constants tagnm", ctx -> "tagnm.id = tags.tag and tagnm.constant ~ " + ctx.insert("^\\w+" + baseTag + ".+"))
                    .joinOn("replace(tagnm.constant, " + tagReplace + ", ':') tagname", SqlCondition.condition("true"))
                    .where(SqlCondition.condition("tags.mod = mods.id"))
                    .groupBy("tags.tag", "tags.replace", "tagname");

            var filArgs = field.getArguments().get("where");
            if (filArgs != null) {
                var applied = new HashSet<String>();
                sub.where(SqlCondition.parseAsCriterion((Map<String, Object>) filArgs, TAG_CRITERIA, applied));

                if (applied.contains("anyEntry")) {
                    sub.joinOn("constants entryname", SqlCondition.condition("entryname.id = tags.entry"));
                }
            }

            var limit = (Integer) field.getArguments().get("limit");
            if (limit != null) {
                sub.limit(limit);
            }

            for (SelectedField req : field.getSelectionSet().getImmediateFields()) {
                switch (req.getName()) {
                    case "name" -> sub.requestColumn("tagname", columnAlias(req));
                    case "entries" -> sub.requestSubColumn("tags t1", b -> {
                        b.requestColumn("coalesce(array_agg(entryname.constant), array[]::text[])", null)
                                .joinOn("constants entryname", SqlCondition.condition("entryname.id = t1.entry"))
                                .where(SqlCondition.condition("t1.tag = tags.tag and t1.mod = mods.id"));

                        var filter = req.getArguments().get("where");
                        if (filter != null) {
                            b.where("entryname.constant", SqlFilter.parse(filter));
                        }
                    }, columnAlias(req));
                    case "replace" -> sub.requestColumn("tags.replace", columnAlias(req));
                }
            }

            builder.columnSubQuery("(" + sub.format() + ")", columnAlias(field), b -> b.requestColumn("coalesce(jsonb_agg(json_out), '[]'::jsonb)", "r"));
        });

        if (requireClassJoin) {
            builder.joinOn("class_defs", SqlCondition.condition("class_defs.mod = mods.id"));
            builder.joinOn("classes", SqlCondition.condition("classes.id = class_defs.type"));
            builder.joinOn("classes", classJoinFilter);

            builder.groupBy("mods.id");
        }
    }

    private String columnAlias(SelectedField field) {
        if (field.getAlias() == null) {
            return field.getName();
        }
        return "$" + field.getAlias();
    }

    private Object paginate(SqlSearchBuilder builder, Pagination pagination, String paginateOn) {
        if (pagination.descending()) {
            builder.orderBy(paginateOn + " desc");
        } else {
            builder.orderBy(paginateOn);
        }

        int limit = pagination.limit();

        if (pagination.after() >= 0) {
            builder.where.addFirst(SqlCondition.columnFilter(SqlFilter.greaterThanOrEqual(pagination.after()), paginateOn));
            limit++;
        }
        if (pagination.before() >= 0) {
            builder.where.addFirst(SqlCondition.columnFilter(SqlFilter.smallerThanOrEqual(pagination.before()), paginateOn));
            limit++;
        }

        limit = Math.max(limit, pagination.limit() + 1); // We need at least one additional element so we can check if we have a next page

        builder.limit(limit);

        var expected = limit;

        return jdbi.withHandle(handle -> builder.build(handle)
                .execute((statementSupplier, ctx) -> {
                    var rs = statementSupplier.get().getResultSet();
                    record ColInfo(String resultName, String type) {}

                    int colCount = rs.getMetaData().getColumnCount();

                    ColInfo[] columns = new ColInfo[colCount + 1];
                    for (int i = 1; i <= colCount; i++) {
                        var colName = rs.getMetaData().getColumnName(i);
                        columns[i] = new ColInfo(builder.lowercasedAliases.getOrDefault(colName, colName), rs.getMetaData().getColumnTypeName(i));
                    }

                    var lst = new ArrayList<Map<String, Object>>(expected);

                    boolean haveAfter = false, haveBefore = false;

                    while (rs.next()) {
                        var entry = HashMap.<String, Object>newHashMap(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            var col = columns[i];
                            if (col.type.startsWith("json")) {
                                var json = rs.getString(i);
                                entry.put(col.resultName, Utils.GSON.fromJson(json, Object.class));
                            } else if (col.type.equals("timestamptz")) {
                                var stamp = rs.getTimestamp(i);
                                entry.put(col.resultName, OffsetDateTime.ofInstant(stamp.toInstant(), ZoneId.systemDefault()));
                            } else {
                                var o = rs.getObject(i);
                                if (o instanceof Array ar) {
                                    entry.put(col.resultName, Arrays.asList((Object[])ar.getArray()));
                                } else {
                                    entry.put(col.resultName, o);
                                }

                                if (col.resultName.equals("id")) {
                                    if (pagination.after() >= 0 && Objects.equals(o, pagination.after())) {
                                        haveAfter = true;
                                    }
                                    if (pagination.before() >= 0 && Objects.equals(o, pagination.before())) {
                                        haveBefore = true;
                                    }
                                }
                            }
                        }
                        lst.add(entry);
                    }

                    class SwappedPointers<T> {
                        boolean normal = true;
                        final List<T> list;

                        SwappedPointers(List<T> list) {
                            this.list = list;
                        }

                        public void removeLast() {
                            if (normal) {
                                list.removeLast();
                            } else {
                                list.removeFirst();
                            }
                        }

                        public void removeFirst() {
                            if (normal) {
                                list.removeFirst();
                            } else {
                                list.removeLast();
                            }
                        }

                        public void swap() {
                            normal = !normal;
                        }
                    }

                    var pointers = new SwappedPointers<>(lst);
                    if (pagination.descending()) {
                        pointers.swap();
                    }

                    if (haveAfter) {
                        pointers.removeFirst();
                    }
                    if (haveBefore) {
                        pointers.removeLast();
                    }

                    boolean hasPrevious = haveAfter, hasNext = haveBefore;

                    while (lst.size() > pagination.limit()) {
                        if (pagination.descending()) {
                            hasPrevious = true;
                        } else {
                            hasNext = true;
                        }
                        lst.removeLast();
                    }

                    if (pagination.descending()) {
                        Collections.reverse(lst);
                    }

                    var pag = HashMap.newHashMap(4);
                    pag.put("hasPreviousPage", hasPrevious);
                    pag.put("hasNextPage", hasNext);
                    if (!lst.isEmpty()) {
                        pag.put("startCursor", Utils.base64(lst.getFirst().get("id")));
                        pag.put("endCursor", Utils.base64(lst.getLast().get("id")));
                    }

                    return Map.of(
                            "edges", lst,
                            "pageInfo", pag,
                            "count", lst.size()
                    );
                }));
    }

    private record Pagination(int limit, boolean descending, int after, int before) {
        public static final Pagination DEFAULT = new Pagination(MAX_ITEMS_PER_REQUEST, false, -1, -1);

        private static Pagination parse(Map<String, Object> args) {
            int limit = MAX_ITEMS_PER_REQUEST;
            boolean isLast = false;

            Integer last = (Integer) args.get("last");
            if (last == null) {
                Integer first = (Integer) args.get("first");
                if (first != null) {
                    limit = Math.min(first, MAX_ITEMS_PER_REQUEST);
                }
            } else {
                limit = Math.min(last, MAX_ITEMS_PER_REQUEST);
                isLast = true;
            }

            int after = -1, before = -1;

            String aft = (String) args.get("after");
            if (aft != null) {
                after = Integer.parseInt(new String(Base64.getDecoder().decode(aft), StandardCharsets.UTF_8));
            }

            String bef = (String) args.get("before");
            if (bef != null) {
                before = Integer.parseInt(new String(Base64.getDecoder().decode(bef), StandardCharsets.UTF_8));
            }

            return new Pagination(limit, isLast, after, before);
        }
    }

    private record Order(OrderRule rule, boolean desc) {
        private Order(Map<String, Object> map) {
            this(OrderRule.valueOf(((String) map.getOrDefault("by", "value")).toUpperCase(Locale.ROOT)), Objects.equals(map.get("direction"), "desc"));
        }

        public String createStatement(String column) {
            return rule.apply(column) + " " + (desc ? "desc" : "asc");
        }
    }

    private enum OrderRule {
        VALUE {
            @Override
            public String apply(String in) {
                return in;
            }
        },
        LENGTH {
            @Override
            public String apply(String in) {
                return "length(" + in + ")";
            }
        };

        public abstract String apply(String in);
    }

    private record InPackCriterion(String curseforgeColumn, String modrinthColumn) implements FilterCriterion.MapOnly {
        @Override
        public SqlCondition apply(Map<String, Object> value) {
            var cf = value.get("curseforge");
            if (cf != null) {
                var file = Main.CURSE_FORGE_PLATFORM.getModById(cf).getAllFiles().next();
                return ctx -> curseforgeColumn + " = any(" + ctx.insert(ids(file)) + "::int[])";
            }
            var mr = value.get("modrinth");
            var file = Main.MODRINTH_PLATFORM.getModById(mr).getAllFiles().next();
            return ctx -> modrinthColumn + " = any(" + ctx.insert(ids(file)) + "::text[])";
        }

        private Object[] ids(PlatformModFile file) {
            return file.getPlatform().getModsInPack(file)
                    .stream().map(PlatformModFile::getModId)
                    .toArray();
        }
    }
}
