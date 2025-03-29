package net.neoforged.waifu.db.sql.search;

import com.google.common.collect.ImmutableBiMap;
import graphql.schema.DataFetchingEnvironment;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.HashPrefixSqlParser;
import org.jdbi.v3.core.statement.SqlStatements;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class SqlSearchHelper implements DatabaseSearchHelper {
    private static final Map<String, FilterCriterion> GENERAL_MOD_CRITERIA = Map.of(
            "name", FilterCriterion.column("mods.name"),
            "authors", FilterCriterion.column("mods.authors"),
            "license", FilterCriterion.column("mods.license"),

            "anyClassName", FilterCriterion.column("classes.name"),

            "inPack", new InPackCriterion("curseforge_project_id", "modrinth_project_id")
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
            "replace", FilterCriterion.column("tags.replace")
    );

    private static final Map<String, String> MOD_FIELD_MAPPING = Map.of(
            "name", "name",
            "authors", "authors",
            "license", "license",
            "id", "id",
            "version", "version",
            "curseforgeProjectId", "curseforge_project_id",
            "modrinthProjectId", "modrinth_project_id",
            "mavenCoordinates", "maven_coordinates"
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
        return baseModQuery(env, builder -> {
            var ids = new ArrayList<>((List<Integer>) env.getArgument("ids"));
            if (ids.size() > MAX_ITEMS_PER_REQUEST) {
                throw new IllegalArgumentException("Found " + ids.size() + " ids to query, more than the maximum of " + MAX_ITEMS_PER_REQUEST);
            }

            builder.expectedItems = ids.size();
            builder.builder.where(ctx -> "mods.id = any(" + ctx.insert(ids) + ")");
        });
    }

    @Override
    public Object getMods(DataFetchingEnvironment env) {
        return baseModQuery(env, builder -> {
            var pagination = Optional.ofNullable(env.<Map<String, Integer>>getArgument("pagination"))
                    .map(m -> new Pagination(
                            Math.min(m.getOrDefault("limit", MAX_ITEMS_PER_REQUEST), MAX_ITEMS_PER_REQUEST),
                            m.getOrDefault("after", -1)
                    ))
                    .orElse(Pagination.DEFAULT);

            if (pagination.after > 0) {
                builder.builder.where("id", SqlFilter.greaterThan(pagination.after));
            }

            builder.builder.limit(pagination.limit() + 1);
            builder.expectedItems = pagination.limit();

            Map<String, Object> filter = env.getArgument("filter");
            if (filter != null) {
                var applied = new HashSet<String>();
                builder.builder.where(SqlCondition.parseAsCriterion(filter, loader == ModLoader.FABRIC ? FABRIC_MOD_CRITERIA : FORGE_MOD_CRITERIA, applied));

                if (applied.contains("anyClassName")) {
                    builder.requireClassJoin = true;
                }
            }
        });
    }

    private static class ModQuery {
        private final SqlSearchBuilder builder;
        private boolean requireClassJoin;

        private int expectedItems;

        private ModQuery(SqlSearchBuilder builder) {
            this.builder = builder;
        }
    }

    @SuppressWarnings("unchecked")
    private Object baseModQuery(DataFetchingEnvironment env, Consumer<ModQuery> cons) {
        var builder = new SqlSearchBuilder("mods");

        builder.requestColumn("mods.id");

        Map<String, String> columnRequests = new HashMap<>();

        MOD_FIELD_MAPPING.forEach((fld, dbMapping) -> {
            if (env.getSelectionSet().contains("mods/" + fld)) {
                builder.requestColumn("mods." + dbMapping);
                columnRequests.put(dbMapping, fld);
            }
        });

        var query = new ModQuery(builder);
        cons.accept(query);

        List<SqlCondition> classJoinFilter = new ArrayList<>();
        if (env.getSelectionSet().contains("mods/classes")) {
            query.requireClassJoin = true;
            var selection = env.getSelectionSet().getFields("mods/classes").getFirst();
            var filArgs = selection.getArguments().get("filter");
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

            builder.requestColumn("(array_agg(" + aggIn + ")::text[])" + limitText + " as classes");
        }

        if (env.getSelectionSet().contains("mods/tags")) {
            var selection = env.getSelectionSet().getFields("mods/tags").getFirst();
            var reg = (String) selection.getArguments().get("registry");

            String baseTag = "/" + reg.replace(':', '/').replace("minecraft/", "") + "/";

            var tagReplace = builder.insert(baseTag);

            var sub = builder.subBuilder("tags")
                    .requestColumn("jsonb_build_object('name', tagname, 'entries', json_agg(entryname.constant), 'replace', tags.replace)", "e")
                    .joinOn("constants entryname", SqlCondition.condition("entryname.id = tags.entry"))
                    .joinOn("constants tagnm", ctx -> "tagnm.id = tags.tag and tagnm.constant ~ " + ctx.insert("^\\w+" + baseTag + ".+"))
                    .joinOn("replace(tagnm.constant, " + tagReplace + ", ':') tagname", SqlCondition.condition("true"))
                    .where(SqlCondition.condition("tags.mod = mods.id"))
                    .groupBy("tagname", "tags.replace");

            var filArgs = selection.getArguments().get("filter");
            if (filArgs != null) {
                sub.where(SqlCondition.parseAsCriterion((Map<String, Object>) filArgs, TAG_CRITERIA, new HashSet<>()));
            }

            builder.columnSubQuery("(" + sub.format() + ")", "tags", b -> b.requestColumn("coalesce(jsonb_agg(e), '[]')"));
        }

        if (query.requireClassJoin) {
            builder.joinOn("class_defs", SqlCondition.condition("class_defs.mod = mods.id"));
            builder.joinOn("classes", SqlCondition.condition("classes.id = class_defs.type"));
            builder.joinOn("classes", classJoinFilter);

            builder.groupBy("mods.id");
        }

        builder.orderBy("mods.id");

        return jdbi.withHandle(handle -> builder.build(handle)
                .execute((statementSupplier, ctx) -> {
                    var rs = statementSupplier.get().getResultSet();
                    record ColInfo(String resultName, String type) {}
                    ColInfo[] columnIds = new ColInfo[builder.columns.size() + 1];
                    for (String field : builder.columns) {
                        var realCol = List.of(field.split(" as ")).getLast();
                        realCol = List.of(realCol.split("\\.")).getLast();
                        int id = rs.findColumn(realCol);
                        columnIds[id] = new ColInfo(
                                columnRequests.getOrDefault(realCol, realCol),
                                rs.getMetaData().getColumnTypeName(id)
                        );
                    }

                    var lst = new ArrayList<Map<String, Object>>(query.expectedItems);

                    while (rs.next()) {
                        var entry = HashMap.<String, Object>newHashMap(builder.columns.size());
                        for (int i = 1; i <= builder.columns.size(); i++) {
                            var col = columnIds[i];
                            if (col.type.startsWith("json")) {
                                var json = rs.getString(i);
                                entry.put(col.resultName, Utils.GSON.fromJson(json, Object.class));
                            } else {
                                var o = rs.getObject(i);
                                if (o instanceof Array ar) {
                                    entry.put(col.resultName, Arrays.asList((Object[])ar.getArray()));
                                } else {
                                    entry.put(col.resultName, o);
                                }
                            }
                        }
                        lst.add(entry);
                    }

                    boolean hasNext = false;

                    if (lst.size() > query.expectedItems) {
                        hasNext = true;
                        lst.removeLast();
                    }

                    var pag = HashMap.newHashMap(3);
                    pag.put("hasNextPage", hasNext);
                    pag.put("size", lst.size());
                    if (!lst.isEmpty()) {
                        pag.put("endCursor", lst.getLast().get("id"));
                    }

                    return Map.of(
                            "mods", lst,
                            "pageInfo", pag
                    );
                }));
    }

    private record Pagination(int limit, int after) {
        public static final Pagination DEFAULT = new Pagination(MAX_ITEMS_PER_REQUEST, -1);
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
