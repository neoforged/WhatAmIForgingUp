/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.metabase.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.matyrobbrt.metabase.MetabaseClient;
import com.matyrobbrt.metabase.RequestParameters;
import com.matyrobbrt.metabase.Route;
import com.matyrobbrt.metabase.internal.MetabaseType;
import com.matyrobbrt.metabase.params.FieldUpdateParameters;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

@MetabaseType
public record Field(
        MetabaseClient client,
        JsonObject _json,

        int id, String name,
        @SerializedName("display_name") String displayName
) {
    private static final Route<Field> UPDATE_FIELD = Route.update("/field/:id", Field.class);
    private static final Route<Void> UPDATE_DIMENSION =  Route.post("/field/:id/dimension", requestParameters -> (JsonElement) requestParameters.getIndex(0), Route.VOID);
    private static final Route<Void> DELETE_DIMENSION =  Route.delete("/field/:id/dimension");

    public CompletableFuture<Field> update(UnaryOperator<FieldUpdateParameters> parameters) {
        return client.sendRequest(UPDATE_FIELD.compile(RequestParameters.of(
                "id", id, "params", parameters.apply(new FieldUpdateParameters(this))
        )));
    }

    public CompletableFuture<Void> setDimension(int targetField, String targetFieldName) {
        final JsonObject structure = new JsonObject();
        structure.addProperty("human_readable_field_id", targetField);
        structure.addProperty("name", targetFieldName);
        structure.addProperty("type", "external");
        return client.sendRequest(UPDATE_DIMENSION, structure, "id", id());
    }

    public CompletableFuture<Void> deleteDimension() {
        return client.sendRequest(DELETE_DIMENSION, "id", id());
    }
}
