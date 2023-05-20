/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.metabase;

import net.minecraftforge.metabase.params.DatabaseQueryParameters;
import net.minecraftforge.metabase.params.DatabasesQueryParameters;
import net.minecraftforge.metabase.types.Database;
import net.minecraftforge.metabase.types.Table;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public interface MetabaseClientRequests {
    Route<Table> GET_TABLE = Route.getEntityByID("table", Table.class);
    Route<List<Table>> GET_TABLES = Route.getEntities("table", Table.class);

    default CompletableFuture<Table> getTable(int id) {
        return sendRequest(GET_TABLE, "id", id);
    }

    default CompletableFuture<List<Table>> getTables() {
        return sendRequest(GET_TABLES);
    }

    Route<Database> GET_DATABASE = Route.getEntityByID("database", Database.class);
    Route<List<Database>> GET_DATABASES = Route.getEntities("database", Database.class);

    default CompletableFuture<Database> getDatabase(int id, UnaryOperator<DatabaseQueryParameters> parameters) {
        return sendRequest(GET_DATABASE, parameters.apply(new DatabaseQueryParameters()), "id", id);
    }

    default CompletableFuture<List<Database>> getDatabases(UnaryOperator<DatabasesQueryParameters> parameters) {
        return sendRequest(GET_DATABASES, parameters.apply(new DatabasesQueryParameters()));
    }

    <T> CompletableFuture<T> sendRequest(Request<T> request);

    default <T> CompletableFuture<T> sendRequest(Route<T> route, Object... parameters) {
        return sendRequest(route.compile(RequestParameters.of(parameters)));
    }
}
