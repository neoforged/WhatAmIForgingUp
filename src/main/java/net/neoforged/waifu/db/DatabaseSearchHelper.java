package net.neoforged.waifu.db;

import graphql.schema.DataFetchingEnvironment;

public interface DatabaseSearchHelper {
    Object getMods(DataFetchingEnvironment env);
    Object getModsById(DataFetchingEnvironment env);
}
