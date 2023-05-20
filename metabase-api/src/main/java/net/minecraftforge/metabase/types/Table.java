/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.metabase.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.metabase.MetabaseClient;
import net.minecraftforge.metabase.RequestParameters;
import net.minecraftforge.metabase.Route;
import net.minecraftforge.metabase.internal.MetabaseType;
import net.minecraftforge.metabase.params.TableUpdateParameters;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

@MetabaseType
public record Table(
        MetabaseClient client,
        int id, @SerializedName("db_id") int dbId,
        boolean active,
        String schema, String name,
        String description,
        List<Field> fields
) {
    private static final Route<Table> UPDATE_TABLE = Route.update("/table/:id", Table.class);
    private static final Route<List<Table>> UPDATE_TABLE_GENERAL = Route.put("/table", requestParameters -> (JsonElement) requestParameters.getIndex(0), Route.decodeType(new TypeToken<>() {}));

    public CompletableFuture<Table> update(UnaryOperator<TableUpdateParameters> parameters) {
        return client.sendRequest(UPDATE_TABLE.compile(RequestParameters.of(
                "id", id, "params", parameters.apply(new TableUpdateParameters())
        )));
    }

    public CompletableFuture<Table> setHidden(boolean hidden) {
        final JsonObject request = new JsonObject();
        request.addProperty("visibility_type", hidden ? "hidden" : null);
        final JsonArray ids = new JsonArray();
        ids.add(id());
        request.add("ids", ids);
        return client.sendRequest(UPDATE_TABLE_GENERAL.compile(RequestParameters.of(
            request
        ))).thenApply(l -> l.get(0));
    }
}
