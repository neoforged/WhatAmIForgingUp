/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import net.neoforged.metabase.params.UpdateParameters;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

public record Route<T>(
        String path,
        RequestMethod method,
        Function<RequestParameters, JsonElement> bodyGetter,
        BiFunction<MetabaseClient, JsonElement, T> decoder
) {
    private static final Function<RequestParameters, JsonElement> NONE = r -> null;
    public static final BiFunction<MetabaseClient, JsonElement, Void> VOID = (client, element) -> null;

    public static <T> Route<T> post(String path, Function<RequestParameters, JsonElement> body, BiFunction<MetabaseClient, JsonElement, T> decoder) {
        return new Route<>(path, RequestMethod.POST, body, decoder);
    }

    public static <T> Route<T> put(String path, Function<RequestParameters, JsonElement> body, BiFunction<MetabaseClient, JsonElement, T> decoder) {
        return new Route<>(path, RequestMethod.PUT, body, decoder);
    }

    public static <T> Route<T> getEntityByID(String entityType, Class<T> type) {
        return new Route<>(
                "/" + entityType + "/:id", RequestMethod.GET,
                NONE, (client, element) -> client.gson.fromJson(element, type)
        );
    }

    public static <T> Route<List<T>> getEntities(String entityType, Class<T> type) {
        return new Route<>(
                "/" + entityType + "/", RequestMethod.GET,
                NONE, (client, element) -> {
                    final JsonArray array;
                    if (element.isJsonObject()) {
                        array = element.getAsJsonObject().getAsJsonArray("data");
                    } else {
                        array = element.getAsJsonArray();
                    }
                    return StreamSupport.stream(array.spliterator(), false)
                            .map(el -> client.gson.fromJson(el, type))
                            .toList();
                }
        );
    }

    public static <T> Route<T> update(String path, Class<T> type) {
        return new Route<>(
                path, RequestMethod.PUT, requestParameters -> ((UpdateParameters) requestParameters.get("params")).compile(),
                (client, element) -> client.gson.fromJson(element, type)
        );
    }

    public static Route<Void> delete(String path) {
        return new Route<>(
                path, RequestMethod.DELETE, NONE, VOID
        );
    }

    public Request<T> compile(RequestParameters parameters) {
        return new Request<>(
                parameters.compilePath(path), method, decoder, bodyGetter.apply(parameters)
        );
    }

    public static <T> BiFunction<MetabaseClient, JsonElement, T> decodeType(TypeToken<T> type) {
        final Type actualType = type.getType();
        return (client, element) -> client.gson.fromJson(element, actualType);
    }
}
