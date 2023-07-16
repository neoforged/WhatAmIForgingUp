/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.types;

import com.google.gson.JsonObject;
import net.neoforged.metabase.MetabaseClient;
import net.neoforged.metabase.RequestParameters;
import net.neoforged.metabase.Route;
import net.neoforged.metabase.internal.MetabaseType;
import net.neoforged.metabase.params.DatabaseUpdateParameters;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

@MetabaseType
public record Database(
        MetabaseClient client,
        int id, List<Table> tables,
        String description, String name,
        JsonObject details
) {
    private static final Route<Database> UPDATE_DATABASE = Route.update("/database/:id", Database.class);
    private static final Route<Void> DELETE_DATABASE = Route.delete("/database/:id");
    private static final Route<Void> SYNC_SCHEMA = Route.post("/database/:id/sync_schema", requestParameters -> new JsonObject(), (client, element) -> null);

    public CompletableFuture<Database> update(UnaryOperator<DatabaseUpdateParameters> parameters) {
        return client.sendRequest(UPDATE_DATABASE.compile(RequestParameters.of(
                "id", id, "params", parameters.apply(new DatabaseUpdateParameters())
        )));
    }

    public CompletableFuture<Void> delete() {
        return client.sendRequest(DELETE_DATABASE, "id", id);
    }

    public CompletableFuture<Void> syncSchema() {
        return client.sendRequest(SYNC_SCHEMA, "id", id);
    }
}
