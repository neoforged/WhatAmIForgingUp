package net.neoforged.waifu.web.api;

import graphql.schema.DataFetchingEnvironment;

public class GraphQLTokenPrivilege<T> {
    public static final GraphQLTokenPrivilege<Integer> PAGINATION_LIMIT = new GraphQLTokenPrivilege<>("pagination_limit", 500);

    private final String key;
    private final T defaultValue;

    private GraphQLTokenPrivilege(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public T get(DataFetchingEnvironment env) {
        return env.getGraphQlContext().getOrDefault(this, defaultValue);
    }

    @Override
    public String toString() {
        return "GraphQLTokenPrivilege[" + key + "]";
    }
}
