package net.neoforged.waifu.db.sql.search;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.HashPrefixSqlParser;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementCustomizer;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.directColumn;
import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.listSubTable;
import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.subTable;
import static net.neoforged.waifu.db.sql.search.DatabaseType.applyLimit;
import static net.neoforged.waifu.db.sql.search.DatabaseType.applyOrder;

@SuppressWarnings("FieldCanBeLocal")
public class SqlSearchHelper implements DatabaseSearchHelper {
    public static final Map<String, FilterCriterion> MANIFEST_CRITERIA = Map.of(
            "name", FilterCriterion.jsonExpression("mods.manifest", "$.*[*].key"),
            "value", FilterCriterion.jsonExpression("mods.manifest", "$.*[*].value")
    );

    private static final Map<String, FilterCriterion> TAG_CRITERIA = Map.of(
            "name", FilterCriterion.column("tagname"),
            "replace", FilterCriterion.column("tags.replace"),
            "anyEntry", FilterCriterion.column("entryname.constant")
    );

    private static final Map<String, FilterCriterion> DATA_MAP_CRITERIA = Map.of(
            "name", FilterCriterion.column("dmapname")
    );

    private static final int MAX_ITEMS_PER_REQUEST = 500;

    private final Jdbi jdbi;
    private final Consumer<Runnable> cancellationInvoker;

    private final DatabaseSchema schema;

    private final DatabaseType mod, classes, class_defs;

    @SuppressWarnings("unchecked")
    public SqlSearchHelper(Jdbi jdbi, ModLoader loader, Consumer<Runnable> cancellationInvoker) {
        this.jdbi = jdbi;
        this.cancellationInvoker = cancellationInvoker;
        jdbi.getConfig(SqlStatements.class).setSqlParser(new HashPrefixSqlParser());

        schema = new DatabaseSchema();

        mod = schema.registerType("mods", b -> b
                .directFields("id", "name", "authors", "license", "version", "manifest")
                .field("curseforgeProjectId", "curseforge_project_id").field("modrinthProjectId", "modrinth_project_id")
                .field("mavenCoordinates", "maven_coordinates")
                .field("indexedOn", "index_date")
                .field("metadata", (type, builder, fieldName, selection) -> {
                    var path = (String) selection.getArguments().get("path");
                    if (path == null) {
                        builder.requestColumn("mods.mod_metadata_json", fieldName);
                    } else {
                        var columnName = "__mod_metadata_json_" + fieldName;
                        builder.joinOn("jsonb_path_query_array(mods.mod_metadata_json, " + builder.insert(path) + "::jsonpath) " + columnName, SqlCondition.TRUE);
                        builder.requestColumn("case when jsonb_array_length(" + columnName + ") = 1 then " + columnName + " -> 0 when jsonb_array_length(" + columnName + ") = 0 then null else " + columnName + " end", fieldName);
                    }
                })

                .filters(Map.of(
                        "name", FilterCriterion.column("mods.name"),
                        "authors", FilterCriterion.column("mods.authors"),
                        "license", FilterCriterion.column("mods.license"),

                        "inPack", new InPackCriterion("curseforge_project_id", "modrinth_project_id"),
                        "curseforgeProjectId", FilterCriterion.column("curseforge_project_id"),
                        "modrinthProjectId", FilterCriterion.column("modrinth_project_id"),

                        "anyManifestAttribute", val -> SqlCondition.parseAsCriterion((Map<String, Object>) val, MANIFEST_CRITERIA)
                ))
                .filterOnColumn("indexed", "index_date", SqlFilter.DATE_TIME_FILTER)
                .filter("modId", FilterCriterion.jsonExpression("mods.mod_metadata_json", loader == ModLoader.FABRIC ? "$.id" : "$.mods[*].modId"))
                .filter("description", FilterCriterion.jsonExpression("mods.mod_metadata_json", loader == ModLoader.FABRIC ? "$.description" : "$.mods[*].description"))
                .filterOnTable("anyClass", "class_defs")

                // Only for complete Mod instances (i.e. not LightweightMod)
                .field("classes", listSubTable("class_defs"))
                .field("enumExtensions", listSubTable("enum_extensions"))

                // TODO - these are special because of the registry, figure out a way not to need to make them special
                .field("tags", (type, builder, fieldName, field) -> builder.arrayAggregateSubQuery("tags", columnAlias(field), sub -> {
                    var reg = (String) field.getArguments().get("registry");

                    String baseTag = "/" + reg.replace(':', '/').replace("minecraft/", "") + "/";

                    var tagReplace = builder.insert(baseTag);

                    sub
                            .joinOn("constants tagnm", ctx -> "tagnm.id = tags.tag and tagnm.constant ~ " + ctx.insert("^\\w+" + baseTag + ".+"))
                            .joinOn("replace(tagnm.constant, " + tagReplace + ", ':') tagname", SqlCondition.TRUE)
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

                    applyLimit(sub, field);

                    for (SelectedField req : field.getSelectionSet().getImmediateFields()) {
                        switch (req.getName()) {
                            case "name" -> sub.requestColumn("tagname", columnAlias(req));
                            case "entries" -> sub.requestSubColumn("tags t1", columnAlias(req), bl -> {
                                bl.requestColumn("coalesce(array_agg(entryname.constant), array[]::text[])", null)
                                        .joinOn("constants entryname", SqlCondition.condition("entryname.id = t1.entry"))
                                        .where(SqlCondition.condition("t1.tag = tags.tag and t1.mod = mods.id"));

                                var filter = req.getArguments().get("where");
                                if (filter != null) {
                                    bl.where("entryname.constant", SqlFilter.STRING_FILTER.apply(filter));
                                }
                            });
                            case "replace" -> sub.requestColumn("tags.replace", columnAlias(req));
                        }
                    }
                }))
                .field("dataMaps", (type, builder, fieldName, field) -> builder.arrayAggregateSubQuery("data_maps", columnAlias(field), sub -> {
                    var reg = (String) field.getArguments().get("registry");

                    String baseDataMap = "/" + reg.replace(':', '/').replace("minecraft/", "") + "/";

                    var dataMapReplace = builder.insert(baseDataMap);

                    sub
                            .joinOn("constants dmapnm", ctx -> "dmapnm.id = data_maps.data_map and dmapnm.constant ~ " + ctx.insert("^\\w+" + baseDataMap + ".+"))
                            .joinOn("replace(dmapnm.constant, " + dataMapReplace + ", ':') dmapname", SqlCondition.TRUE)
                            .where(SqlCondition.condition("data_maps.mod = mods.id"))
                            .groupBy("data_maps.data_map", "dmapname");

                    var filArgs = field.getArguments().get("where");
                    if (filArgs != null) {
                        sub.where(SqlCondition.parseAsCriterion((Map<String, Object>) filArgs, DATA_MAP_CRITERIA));
                    }

                    applyLimit(sub, field);

                    for (SelectedField req : field.getSelectionSet().getImmediateFields()) {
                        switch (req.getName()) {
                            case "name" -> sub.requestColumn("dmapname", columnAlias(req));
                            case "entries" -> sub.requestSubColumn("data_maps t1", columnAlias(req), bl -> {
                                bl.requestColumn("coalesce(jsonb_agg(json_build_object('key', entrykey.constant, 'value', entryvalue.constant, 'replace', t1.replace)), '[]'::jsonb)", null)
                                        .joinOn("constants entrykey", SqlCondition.condition("entrykey.id = t1.key"))
                                        .joinOn("json_constants entryvalue", SqlCondition.condition("entryvalue.id = t1.value"))
                                        .where(SqlCondition.condition("t1.data_map = data_maps.data_map and t1.mod = mods.id"));
                            });
                        }
                    }
                }))

                .groupBy("mods.id"));

        var class_annotations = registerAnnotationType("class_annotations");
        var method_annotations = registerAnnotationType("method_annotations");
        var field_annotations = registerAnnotationType("field_annotations");

        var methods = schema.registerType("methods", b -> b
                .field("name", "method_name.constant")
                .field("descriptor", "method_desc.constant")
                .field("definitions", listSubTable("method_defs"))
                .field("references", listSubTable("method_references"))

                .filterOnColumn("name", "method_name.constant")
                .filterOnColumn("descriptor", "method_desc.constant")
                .filterOnTable("anyReference", "method_references")
        );
        schema.registerIndependentJoin("methods", "classes", "method_class", SqlCondition.equals("methods.cls", "method_class.id"));
        schema.registerIndependentJoin("methods", "constants", "method_name", SqlCondition.equals("methods.name", "method_name.id"));
        schema.registerIndependentJoin("methods", "constants", "method_desc", SqlCondition.equals("methods.descriptor", "method_desc.id"));

        var method_defs = schema.registerType("method_defs", b -> b
                .field("name", "method_name.constant")
                .field("descriptor", "method_desc.constant")
                .field("annotations", listSubTable(method_annotations))

                .filterOnColumn("name", "method_name.constant")
                .filterOnColumn("descriptor", "method_desc.constant")
        );
        schema.registerTwoWayJoin("methods", "method_defs", SqlCondition.equals("methods.id", "method_defs.type"));
        schema.registerTwoWayJoin("method_defs", "method_annotations", SqlCondition.equals("method_defs.id", "method_annotations.owner"));
        schema.registerTwoWayJoin("method_defs", "class_defs", SqlCondition.equals("method_defs.owner", "class_defs.id"));

        var method_references = schema.registerType("method_references", b -> b
                .field("name", "method_name.constant")
                .field("descriptor", "method_desc.constant")
                .field("class", "method_class.name")
                .field("referenceCount", "method_references.count")
                .field("owner", subTable("class_defs"))

                .filterOnTable("mod", "mods")
                .filterOnColumn("referenceCount", "method_references.count", SqlFilter.INT_FILTER)
        );
        schema.registerTwoWayJoin("method_references", "methods", SqlCondition.equals("method_references.reference", "methods.id"));
        schema.registerTwoWayJoin("class_defs", "method_references", SqlCondition.equals("class_defs.id", "method_references.owner"));

        var fields = schema.registerType("fields", b -> b
                .field("name", "field_name.constant")
                .field("type", "field_type.name")
                .field("definitions", listSubTable("field_defs"))
                .field("references", listSubTable("field_references"))

                .filterOnColumn("name", "field_name.constant")
                .filterOnColumn("type", "field_type.name")
                .filterOnTable("anyReference", "field_references")
        );
        schema.registerIndependentJoin("fields", "classes", "field_class", SqlCondition.equals("fields.cls", "field_class.id"));
        schema.registerIndependentJoin("fields", "constants", "field_name", SqlCondition.equals("fields.name", "field_name.id"));
        schema.registerIndependentJoin("fields", "classes", "field_type", SqlCondition.equals("fields.descriptor", "field_type.id"));

        var field_defs = schema.registerType("field_defs", b -> b
                .field("name", "field_name.constant")
                .field("type", "field_type.name")
                .field("annotations", listSubTable(field_annotations))

                .filterOnColumn("name", "field_name.constant")
                .filterOnColumn("type", "field_type.name")
        );
        schema.registerTwoWayJoin("fields", "field_defs", SqlCondition.equals("fields.id", "field_defs.type"));
        schema.registerTwoWayJoin("field_defs", "field_annotations", SqlCondition.equals("field_defs.id", "field_annotations.owner"));
        schema.registerTwoWayJoin("field_defs", "class_defs", SqlCondition.equals("field_defs.owner", "class_defs.id"));

        var field_references = schema.registerType("field_references", b -> b
                .field("name", "field_name.constant")
                .field("type", "field_type.name")
                .field("class", "field_class.name")
                .field("referenceCount", "field_references.count")
                .field("owner", subTable("class_defs"))

                .filterOnTable("mod", "mods")
                .filterOnColumn("referenceCount", "field_references.count", SqlFilter.INT_FILTER)
        );
        schema.registerTwoWayJoin("field_references", "fields", SqlCondition.equals("field_references.reference", "fields.id"));
        schema.registerTwoWayJoin("class_defs", "field_references", SqlCondition.equals("class_defs.id", "field_references.owner"));

        class_defs = schema.registerType("class_defs", b -> b
                .field("name", "classes.name")
                .field("parents", (type, builder, fieldName, selection) -> builder
                        .requestSubColumn("class_parents", fieldName, c -> c
                                .joinOn("classes parent", "parent.id = class_parents.parent")
                                .where("class_parents.cls = class_defs.id")
                                .requestColumn("array_agg(parent.name)", null)))
                .field("mod", subTable(mod))
                .field("annotations", listSubTable(class_annotations))

                .field("methods", listSubTable(method_defs))
                .field("fields", listSubTable(field_defs))
                .field("referencedMethods", listSubTable(method_references))
                .field("referencedFields", listSubTable(field_references))

                .filterOnColumn("name", "classes.name")
                .filterOnTable("mod", mod)
                .filterOnTable("anyMethod", "method_defs")
                .filterOnTable("anyField", "field_defs")
                .filterOnTable("anyAnnotation", class_annotations)

                .filter("anyParent", value -> builder -> {
                    var sub = builder.subBuilder("class_parents");
                    sub.where(SqlCondition.equals("class_defs.id", "class_parents.cls"));
                    sub.joinOn("classes parent", "parent.id = class_parents.parent");
                    sub.where("parent.name", SqlFilter.STRING_FILTER.apply(value));
                    return "exists (" + sub.requestColumn("*", null).format() + ")";
                })
        );

        schema.registerTwoWayJoin("class_defs", "class_annotations", SqlCondition.equals("class_defs.id", "class_annotations.owner"));

        schema.registerTwoWayJoin("mods", "class_defs", SqlCondition.condition("class_defs.mod = mods.id"));
        schema.registerTwoWayJoin("classes", "class_defs", SqlCondition.condition("class_defs.type = classes.id"));

        // TODO We have an independent join from method/field->class. Check if we still need it to prevent conflicts
        schema.registerForwardJoin("classes", "methods", SqlCondition.equals("classes.id", "methods.cls"));
        schema.registerForwardJoin("classes", "fields", SqlCondition.equals("classes.id", "fields.cls"));

        classes = schema.registerType("classes", b -> b
                .directFields("id", "name")

                .field("methods", listSubTable(methods))
                .field("fields", listSubTable(fields))
                .field("definitions", listSubTable(class_defs))

                .filterOnColumn("name", "classes.name")
                .filterOnTable("anyMethod", methods)
                .filterOnTable("anyField", fields)

                .groupBy("classes.id")

                // Only for Class
                .field("inheritors", (type, builder, fieldName, selection) -> builder.arrayAggregateSubQuery("classes", fieldName, sub -> {
                    sub.joinOn("classes", SqlCondition.equals("child_classes.type", "classes.id"));
                    schema.getType("classes").apply(sub, selection);
                    applyLimit(sub, selection);
                    applyOrder(type, sub, selection);

                    // This is a bit of a hack, but avoiding it requires rethinking how automatic joining works
                    // We first pretend to query "classes" so that any further attempts of joining start from there
                    // and then after we apply all joins we change the table back to the real one
                    sub.setTable("get_child_classes(classes.id) child_classes");
                }))

                // Only for InheritanceTreeClass
                .field("depth", directColumn("child_classes.depth"))
                .filter("depth", FilterCriterion.column("child_classes.depth", SqlFilter.INT_FILTER))
        );

        schema.registerType("enum_extensions", b -> b
                .field("enum", "extension_enum.name")
                .field("constructor", "extension_ctor.constant")
                .field("name", "extension_name.constant")
                .directFields("parameters")

                .filterOnColumn("enum", "extension_enum.name")
                .filterOnColumn("constructor", "extension_ctor.constant")
                .filterOnColumn("name", "extension_name.constant")
        );
        schema.registerIndependentJoin("enum_extensions", "classes", "extension_enum", SqlCondition.equals("enum_extensions.enum", "extension_enum.id"));
        schema.registerIndependentJoin("enum_extensions", "constants", "extension_name", SqlCondition.equals("enum_extensions.name", "extension_name.id"));
        schema.registerIndependentJoin("enum_extensions", "constants", "extension_ctor", SqlCondition.equals("enum_extensions.constructor", "extension_ctor.id"));

        schema.registerTwoWayJoin("mods", "enum_extensions", SqlCondition.equals("mods.id", "enum_extensions.mod"));
    }

    private DatabaseType registerAnnotationType(String annotationTable) {
        schema.registerIndependentJoin(annotationTable, "classes", "annotation_type", SqlCondition.equals(annotationTable + ".annotation", "annotation_type.id"));
        schema.registerIndependentJoin(annotationTable, "json_constants", "annotation_value", SqlCondition.equals(annotationTable + ".value", "annotation_value.id"));

        return schema.registerType(annotationTable, b -> b
                .field("type", "annotation_type.name")
                .field("value", "annotation_value.constant")

                .filterOnColumn("type", "annotation_type.name")
                .filterOnColumn("value", "annotation_value.constant", SqlFilter.JSON_FILTER)
        );
    }

    @Override
    public Object getModsById(DataFetchingEnvironment env) {
        var builder = mod.createQuery();
        builder.requestColumn("mods.id", "id");

        var mods = env.getSelectionSet().getFields("edges/node");
        if (!mods.isEmpty()) {
            mod.apply(builder, mods.getFirst());
        }

        var ids = new ArrayList<>(env.<List<Integer>>getArgument("ids"));

        builder.where(ctx -> "mods.id = any(" + ctx.insert(ids) + ")");

        return paginate(builder, Pagination.parse(env.getArguments()), "mods.id");
    }

    @Override
    public Object getMods(DataFetchingEnvironment env) {
        var builder = mod.createQuery();
        builder.requestColumn("mods.id", "id");

        var mods = env.getSelectionSet().getFields("edges/node");
        if (!mods.isEmpty()) {
            mod.apply(builder, mods.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            mod.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "mods.id");
    }

    @Override
    public Object getClasses(DataFetchingEnvironment env) {
        var builder = classes.createQuery();
        builder.requestColumn("classes.id", "id");

        var classes = env.getSelectionSet().getFields("edges/node");
        if (!classes.isEmpty()) {
            this.classes.apply(builder, classes.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            this.classes.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "classes.id");
    }

    @Override
    public Object getClassDefinitions(DataFetchingEnvironment env) {
        var builder = class_defs.createQuery();
        builder.requestColumn("class_defs.id", "id");

        var classes = env.getSelectionSet().getFields("edges/node");
        if (!classes.isEmpty()) {
            this.class_defs.apply(builder, classes.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            this.class_defs.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "class_defs.id");
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
                .addCustomizer(new StatementCustomizer() {
                    PreparedStatement stmt;
                    {
                        cancellationInvoker.accept(() -> {
                            if (stmt != null) {
                                try {
                                    stmt.cancel();
                                } catch (SQLException ignored) {
                                }
                            }
                        });
                    }

                    @Override
                    public void beforeExecution(PreparedStatement stmt, StatementContext ctx) throws SQLException {
                        this.stmt = stmt;
                    }

                    @Override
                    public void afterExecution(PreparedStatement stmt, StatementContext ctx) throws SQLException {
                        this.stmt = null;
                    }
                })
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
