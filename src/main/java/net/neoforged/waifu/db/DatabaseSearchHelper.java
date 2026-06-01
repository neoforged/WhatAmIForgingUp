package net.neoforged.waifu.db;

import graphql.schema.DataFetchingEnvironment;

public interface DatabaseSearchHelper {
    Object getMods(DataFetchingEnvironment env);
    Object getModsById(DataFetchingEnvironment env);

    Object getClasses(DataFetchingEnvironment env);
    Object getClass(DataFetchingEnvironment env);

    Object getClassDefinitions(DataFetchingEnvironment env);

    Object getRecipes(DataFetchingEnvironment env);
    Object getDataMaps(DataFetchingEnvironment env);
    Object getTagEntries(DataFetchingEnvironment env);
    Object getDataFiles(DataFetchingEnvironment env);
    Object getEnumExtensions(DataFetchingEnvironment env);

    // Internal
    Object getInternalModInformation(DataFetchingEnvironment env);
}
