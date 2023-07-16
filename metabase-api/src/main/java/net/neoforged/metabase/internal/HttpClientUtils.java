/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpClientUtils {
    private static final Gson GSON = new Gson();

    public static HttpRequest.BodyPublisher jsonPublisher(Map<String, ?> parameters) {
        return HttpRequest.BodyPublishers.ofString(GSON.toJson(parameters));
    }

    public static HttpRequest.BodyPublisher jsonPublisher(JsonElement element) {
        return HttpRequest.BodyPublishers.ofString(element.toString());
    }

    public static HttpResponse.BodyHandler<JsonElement> ofJson() {
        final var upstream = HttpResponse.BodySubscribers.ofInputStream();
        return responseInfo -> HttpResponse.BodySubscribers.mapping(
                upstream,
                (final InputStream is) -> {
                    try (final Reader reader = new InputStreamReader(is)) {
                        return GSON.fromJson(reader, JsonElement.class);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
}
