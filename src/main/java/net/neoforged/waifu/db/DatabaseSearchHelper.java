package net.neoforged.waifu.db;

import graphql.schema.DataFetchingEnvironment;

public interface DatabaseSearchHelper {
    Object getMods(DataFetchingEnvironment env);
    Object getModsById(DataFetchingEnvironment env);

    Object getClasses(DataFetchingEnvironment env);

    Object getClassDefinitions(DataFetchingEnvironment env);

    Object getRecipes(DataFetchingEnvironment env);
}
