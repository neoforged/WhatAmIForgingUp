package net.neoforged.waifu.db.sql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.db.sql.search.DatabaseSchema;
import net.neoforged.waifu.db.sql.search.DatabaseType;
import net.neoforged.waifu.db.sql.search.FilterCriterion;
import net.neoforged.waifu.db.sql.search.SqlCondition;
import net.neoforged.waifu.db.sql.search.SqlFilter;
import net.neoforged.waifu.db.sql.search.SqlSearchBuilder;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformMod;
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
import java.sql.ResultSet;
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
import java.util.stream.Stream;

import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.directColumn;
import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.listSubTable;
import static net.neoforged.waifu.db.sql.search.DatabaseType.QueryBuilder.subTable;
import static net.neoforged.waifu.db.sql.search.DatabaseType.applyLimit;
import static net.neoforged.waifu.db.sql.search.DatabaseType.applyOrder;

@SuppressWarnings("FieldCanBeLocal")
public class SQLSearchHelper implements DatabaseSearchHelper {
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

    private final DatabaseType recipes, enum_extensions, data_files, mod, classes, class_defs;

    @SuppressWarnings("unchecked")
    public SQLSearchHelper(Jdbi jdbi, String gameVersion, ModLoader loader, Consumer<Runnable> cancellationInvoker) {
        this.jdbi = jdbi;
        this.cancellationInvoker = cancellationInvoker;
        jdbi.getConfig(SqlStatements.class).setSqlParser(new HashPrefixSqlParser());

        schema = new DatabaseSchema();

        recipes = schema.registerType("recipes", b -> b
                .field("name", "recipe_name.constant")
                .field("type", "recipe_type.constant")
                .field("recipe", "recipes.value")

                .field("mod", subTable("mods"))

                .filterOnColumn("name", "recipe_name.constant")
                .filterOnColumn("type", "recipe_type.constant")
                .filterOnColumn("recipe", "recipes.value", SqlFilter.JSON_FILTER)
        );
        schema.registerIndependentJoin("recipes", "constants", "recipe_name", SqlCondition.equals("recipes.name", "recipe_name.id"));
        schema.registerIndependentJoin("recipes", "constants", "recipe_type", SqlCondition.equals("recipes.type", "recipe_type.id"));

        schema.registerTwoWayJoin("mods", "recipes", SqlCondition.equals("mods.id", "recipes.mod"));

        enum_extensions = schema.registerType("enum_extensions", b -> b
                .field("enum", "extension_enum.name")
                .field("constructor", "extension_ctor.constant")
                .field("name", "extension_name.constant")
                .directFields("parameters")

                .field("mod", subTable("mods"))

                .filterOnColumn("enum", "extension_enum.name")
                .filterOnColumn("constructor", "extension_ctor.constant")
                .filterOnColumn("name", "extension_name.constant")
        );
        schema.registerIndependentJoin("enum_extensions", "classes", "extension_enum", SqlCondition.equals("enum_extensions.enum", "extension_enum.id"));
        schema.registerIndependentJoin("enum_extensions", "constants", "extension_name", SqlCondition.equals("enum_extensions.name", "extension_name.id"));
        schema.registerIndependentJoin("enum_extensions", "constants", "extension_ctor", SqlCondition.equals("enum_extensions.constructor", "extension_ctor.id"));

        schema.registerTwoWayJoin("mods", "enum_extensions", SqlCondition.equals("mods.id", "enum_extensions.mod"));

        data_files = schema.registerType("data_files", b -> b
                .<String>passArgument(
                        "location",
                        "data_file_base_path",
                        path -> "/" + path.replace(':', '/').replace("minecraft/", "") + "/"
                )
                .<String>passArgument(
                        "location",
                        "data_file_path_regex",
                        path -> "^\\w+/" + path.replace(':', '/').replace("minecraft/", "") + "/.+"
                )

                .directFields("value")
                .field("name", "data_file_name.data_file_name")
                .field("mod",  subTable("mods"))

                .filterOnColumn("name", "data_file_name.data_file_name")
                .filterOnColumn("value", "data_files.value",  SqlFilter.JSON_FILTER)

                .twoWayJoin("mods", SqlCondition.equals("data_files.mod", "mods.id")));

        schema.joinChain("data_files")
                .to("constants", "data_file_path", SqlCondition.allOf(List.of(
                        SqlCondition.equals("data_file_path.id", "data_files.path"),
                        SqlCondition.condition("data_file_path.constant ~ ${data_file_path_regex}")
                )))
                .to("replace(data_file_path.constant, ${data_file_base_path}, ':')", "data_file_name", SqlCondition.TRUE);

        mod = schema.registerType("mods", b -> b
                .directFields("id", "name", "authors", "license", "version", "manifest")
                .field("modIds", directColumn("jsonb_path_query_array(mods.mod_metadata_json, '" + (loader == ModLoader.FABRIC ? "$.id" : "$.mods[*].modId") + "')"))

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
                .field("nestedArtifacts", "mods.nested_tree")
                .field("nestedArtifactsFlat", directColumn("jsonb_path_query_array(mods.nested_tree, '$[*].** ? (@.id != null)')"))

                .filterOnColumn("name", "mods.name")
                .filterOnColumn("authors", "mods.authors")
                .filterOnColumn("license", "mods.license")
                .filterOnColumn("mavenCoordinates", "mods.maven_coordinates")
                .filterOnColumn("curseforgeProjectId", "mods.curseforgeProjectId")
                .filterOnColumn("modrinthProjectId", "mods.modrinth_project_id")
                .filter("inPack", new InPackCriterion(
                        "mods.curseforge_project_id", "mods.modrinth_project_id", "mods.maven_coordinates",
                        gameVersion, loader
                ))

                .filterWithSubQuery("anyManifestAttribute", "json_table(mods.manifest, '$.*[*]' columns (key text path '$.key', value text path '$.value')) as man", Map.of(
                        "name", FilterCriterion.column("man.key"),
                        "value", FilterCriterion.column("man.value")
                ))
                .filterWithSubQuery("anyNestedArtifact", "json_table(mods.nested_tree, '$[*].** ? (@.id != null)' columns (id text path '$.id', version text path '$.version')) as nested", Map.of(
                        "id", FilterCriterion.column("nested.id"),
                        "version", FilterCriterion.column("nested.version")
                ))
                .filterOnColumn("indexed", "mods.index_date", SqlFilter.DATE_TIME_FILTER)
                .filter("modId", FilterCriterion.jsonExpression("mods.mod_metadata_json", loader == ModLoader.FABRIC ? "$.id" : "$.mods[*].modId"))
                .filter("description", FilterCriterion.jsonExpression("mods.mod_metadata_json", loader == ModLoader.FABRIC ? "$.description" : "$.mods[*].description"))
                .filterOnTable("anyClass", "class_defs")

                // Only for complete Mod instances (i.e. not LightweightMod)
                .field("classes", listSubTable("class_defs"))
                .field("enumExtensions", listSubTable(enum_extensions))
                .field("recipes", listSubTable(recipes))
                .field("dataFiles", listSubTable(data_files))

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
                                    bl.where("entryname.constant", SqlFilter.STRING_FILTER.parse(filter));
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
                .directFields("id")
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
                .directFields("id")
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
                    sub.where("parent.name", SqlFilter.STRING_FILTER.parse(value));
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
    public Object getClass(DataFetchingEnvironment env) {
        var builder = classes.createQuery()
                .where(ctx -> "classes.name = " + ctx.insert(env.getArgument("name")));
        classes.apply(builder, getEnvSelection(env));

        return executeQuery(builder, (rs, ctx, columns) -> {
            if (!rs.next()) {
                return null;
            }

            var map = HashMap.<String, Object>newHashMap(rs.getMetaData().getColumnCount());

            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                var col = columns[i];
                map.put(col.resultName(), col.read(rs, i));
            }

            return map;
        });
    }

    @Override
    public Object getClassDefinitions(DataFetchingEnvironment env) {
        var builder = class_defs.createQuery();

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

    @Override
    public Object getRecipes(DataFetchingEnvironment env) {
        var builder = recipes.createQuery();

        var recipes = env.getSelectionSet().getFields("edges/node");
        if (!recipes.isEmpty()) {
            this.recipes.apply(builder, recipes.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            this.recipes.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "recipes.mod", "recipes.name");
    }

    @Override
    public Object getDataFiles(DataFetchingEnvironment env) {
        var builder = data_files.createQuery();
        this.data_files.applyQueryArguments(builder, getEnvSelection(env));

        var dataFiles = env.getSelectionSet().getFields("edges/node");
        if (!dataFiles.isEmpty()) {
            this.data_files.apply(builder, dataFiles.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            this.data_files.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "data_files.mod", "data_files.path");
    }

    @Override
    public Object getEnumExtensions(DataFetchingEnvironment env) {
        var builder = enum_extensions.createQuery();

        var extensions = env.getSelectionSet().getFields("edges/node");
        if (!extensions.isEmpty()) {
            this.enum_extensions.apply(builder, extensions.getFirst());
        }

        Map<String, Object> filter = env.getArgument("where");
        if (filter != null) {
            this.enum_extensions.applyFilter(builder, filter);
        }

        return paginate(builder, Pagination.parse(env.getArguments()), "enum_extensions.mod", "enum_extensions.enum", "enum_extensions.name");
    }

    private static SelectedField getEnvSelection(DataFetchingEnvironment env) {
        return env.getSelectionSet().getImmediateFields().getFirst().getParentField();
    }

    private String columnAlias(SelectedField field) {
        if (field.getAlias() == null) {
            return field.getName();
        }
        return "$" + field.getAlias();
    }

    private record ColInfo(String resultName, String type) {
        public Object read(ResultSet rs, int index) throws SQLException {
            if (type.startsWith("json")) {
                var json = rs.getString(index);
                return Utils.GSON.fromJson(json, Object.class);
            } else if (type.equals("timestamptz")) {
                var stamp = rs.getTimestamp(index);
                return OffsetDateTime.ofInstant(stamp.toInstant(), ZoneId.systemDefault());
            } else {
                var o = rs.getObject(index);
                if (o instanceof Array ar) {
                    return Arrays.asList((Object[]) ar.getArray());
                }
                return o;
            }
        }
    }
    private interface DBResultProducer<R> {
        R produce(ResultSet rs, StatementContext ctx, ColInfo[] columns) throws SQLException;
    }

    private <T> T executeQuery(SqlSearchBuilder builder, DBResultProducer<T> producer) {
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

                    int colCount = rs.getMetaData().getColumnCount();

                    ColInfo[] columns = new ColInfo[colCount + 1];
                    for (int i = 1; i <= colCount; i++) {
                        var colName = rs.getMetaData().getColumnName(i);
                        columns[i] = new ColInfo(builder.getRealName(colName), rs.getMetaData().getColumnTypeName(i));
                    }

                    return producer.produce(rs, ctx, columns);
                }));
    }

    private Object paginate(SqlSearchBuilder builder, Pagination pagination, String... paginateOn) {
        builder.requestColumn(paginateOn.length == 1 ? paginateOn[0] : ("array[" + String.join(", ", paginateOn) + "]"), "id");

        for (String pag : paginateOn) {
            if (pagination.descending()) {
                builder.orderBy(pag + " desc");
            } else {
                builder.orderBy(pag);
            }
        }

        // We need at least one additional element so we can check if we have a next page
        int limit = pagination.limit() + 1;

        if (!pagination.after().isEmpty()) {
            builder.primaryWhere(buildWhereClause(pagination.after(), paginateOn, false));
            limit++;
        }
        if (!pagination.before().isEmpty()) {
            builder.primaryWhere(buildWhereClause(pagination.before(), paginateOn, true));
            limit++;
        }

        builder.limit(limit);

        var expected = limit;

        return executeQuery(builder, (rs, ctx, columns) -> {
            int colCount = rs.getMetaData().getColumnCount();
            var lst = new ArrayList<Map<String, Object>>(expected);

            boolean haveAfter = false, haveBefore = false;

            while (rs.next()) {
                var entry = HashMap.<String, Object>newHashMap(colCount);
                for (int i = 1; i <= colCount; i++) {
                    var col = columns[i];
                    var object = col.read(rs, i);

                    entry.put(col.resultName(), object);

                    if (col.resultName().equals("id")) {
                        if (!pagination.after().isEmpty() && !haveAfter && Objects.equals(object instanceof List<?> ? object : List.of(object), pagination.after())) {
                            haveAfter = true;
                        }
                        if (!pagination.before().isEmpty() && !haveBefore && Objects.equals(object instanceof List<?> ? object : List.of(object), pagination.before())) {
                            haveBefore = true;
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
                pag.put("startCursor", Utils.cursorEncode(lst.getFirst().get("id")));
                pag.put("endCursor", Utils.cursorEncode(lst.getLast().get("id")));
            }

            return Map.of(
                    "edges", lst,
                    "pageInfo", pag,
                    "count", lst.size()
            );
        });
    }

    private static SqlCondition buildWhereClause(List<Integer> cursorComponents, String[] paginateOn, boolean descending) {
        return ctx -> {
            List<String> clauses = new ArrayList<>();

            for (int i = 0; i < cursorComponents.size(); i++) {
                StringBuilder clause = new StringBuilder("(");
                for (int j = 0; j < i; j++) {
                    clause.append(paginateOn[j])
                            .append(" = ")
                            .append(cursorComponents.get(j))
                            .append(" and ");
                }

                clause.append(paginateOn[i])
                        .append(i == cursorComponents.size() - 1 ? (descending ? " <= " : " >= ") : (descending ? " < " : " > "))
                        .append(cursorComponents.get(i))
                        .append(")");
                clauses.add(clause.toString());
            }

            return String.join(" or ", clauses);
        };
    }

    private record Pagination(int limit, boolean descending, List<Integer> after, List<Integer> before) {
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

            List<Integer> after = List.of(), before = List.of();

            String aft = (String) args.get("after");
            if (aft != null) {
                after = Stream.of(new String(Base64.getDecoder().decode(aft), StandardCharsets.UTF_8).split(","))
                        .map(Integer::valueOf).toList();
            }

            String bef = (String) args.get("before");
            if (bef != null) {
                before = Stream.of(new String(Base64.getDecoder().decode(bef), StandardCharsets.UTF_8).split(","))
                        .map(Integer::valueOf).toList();
            }

            return new Pagination(limit, isLast, after, before);
        }
    }

    private record InPackCriterion(
            String curseforgeColumn, String modrinthColumn, String mavenCoordinatesColumn,
            String gameVersion, ModLoader loader
    ) implements FilterCriterion.MapOnly {
        private static final String BASE_JIJ_QUERY = "select array_agg(distinct(artifacts->>'id')) from mods join jsonb_path_query(mods.nested_tree, '$[*].** ? (@.id != null)') artifacts on true where mods.nested_tree is not null and ";

        @Override
        public SqlCondition apply(Map<String, Object> in) {
            var entry = in.entrySet().stream().findFirst().orElseThrow();
            var value = entry.getValue();
            return switch (entry.getKey()) {
                case "curseforge" -> applyCurseforge(Objects.requireNonNull(Main.CURSE_FORGE_PLATFORM.getModById(value), () -> "Unknown CurseForge modpack with ID " + value));
                case "curseforgeSlug" -> applyCurseforge(Objects.requireNonNull(Main.CURSE_FORGE_PLATFORM.getModBySlug((String) value, ModPlatform.ProjectType.MODPACK), () -> "Unknown CurseForge modpack with slug " + value));

                case "modrinth" -> applyModrinth(Objects.requireNonNull(Main.MODRINTH_PLATFORM.getModById(value), () -> "Unknown Modrinth modpack with ID " + value));
                case "modrinthSlug" -> applyModrinth(Objects.requireNonNull(Main.MODRINTH_PLATFORM.getModBySlug((String) value, ModPlatform.ProjectType.MODPACK), () -> "Unknown Modrinth modpack with slug " + value));
                default -> throw new IllegalArgumentException();
            };
        }

        private SqlCondition applyCurseforge(PlatformMod mod) {
            var file = mod.getFilesForVersion(gameVersion, loader).next();
            return ctx -> {
                var testExpression = " = any(" + ctx.insert(ids(file)) + "::int[])";
                var jijQuery = BASE_JIJ_QUERY + curseforgeColumn + testExpression;
                return "(" + curseforgeColumn + testExpression + " or " + mavenCoordinatesColumn + " = any((" + jijQuery + ")::text[]))";
            };
        }

        private SqlCondition applyModrinth(PlatformMod mod) {
            var file = mod.getFilesForVersion(gameVersion, loader).next();
            return ctx -> {
                var testExpression = " = any(" + ctx.insert(ids(file)) + "::text[])";
                var jijQuery = BASE_JIJ_QUERY + modrinthColumn + testExpression;
                return "(" + modrinthColumn + testExpression + " or " + mavenCoordinatesColumn + " = any((" + jijQuery + ")::text[]))";
            };
        }

        private Object[] ids(PlatformModFile file) {
            return file.getPlatform().getModsInPack(file)
                    .stream().map(PlatformModFile::getModId)
                    .toArray();
        }
    }
}
