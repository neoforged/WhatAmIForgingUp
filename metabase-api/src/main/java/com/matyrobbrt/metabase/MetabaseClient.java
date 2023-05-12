package com.matyrobbrt.metabase;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.matyrobbrt.metabase.internal.HttpClientUtils;
import com.matyrobbrt.metabase.internal.MetabaseTypeAdapterFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MetabaseClient implements MetabaseClientRequests {
    public static final long MAX_TOKEN_LIFE = Duration.ofDays(1).toMillis();

    private final URI baseURL;
    private final String username;
    private final String password;
    public final Gson gson;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public MetabaseClient(String baseURL, String username, String password) {
        this.baseURL = URI.create(baseURL);
        this.username = username;
        this.password = password;
        this.gson = new GsonBuilder()
                .registerTypeAdapterFactory(new MetabaseTypeAdapterFactory(this))
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return false;
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return clazz == MetabaseClient.class;
                    }
                })
                .create();
    }

    @Override
    public <T> CompletableFuture<T> sendRequest(Request<T> request) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(baseURL.resolve(request.path().startsWith("/") ? "/api" + request.path() : "/api/" + request.path()))
                .header("X-Metabase-Session", getToken())
                .header("Accept", "application/json");

        switch (request.method()) {
            case GET -> builder.GET();
            case DELETE -> builder.DELETE();
            case POST -> builder.POST(HttpClientUtils.jsonPublisher(request.body())).header("Content-Type", "application/json");
            case PUT -> builder.PUT(HttpClientUtils.jsonPublisher(request.body())).header("Content-Type", "application/json");
        }

        return client.sendAsync(builder.build(), HttpClientUtils.ofJson())
                .thenApply(response -> {
                    if (response.statusCode() == 204) return null;
                    if (response.statusCode() != 200) {
                        throw new StatusCodeException(response.statusCode(), request.path());
                    }
                    return request.mapper().apply(this, response.body());
                });
    }

    private String cachedToken;
    private long tokenExpiration;

    private String getToken() {
        if (cachedToken != null && tokenExpiration > System.currentTimeMillis()) {
            return cachedToken;
        }

        try {
            final var request = HttpRequest.newBuilder(baseURL.resolve("/api/session"))
                    .POST(HttpClientUtils.jsonPublisher(
                            Map.of(
                                    "username", username,
                                    "password", password
                            )
                    ))
                    .header("Content-Type", "application/json")
                    .build();
            final var response = client.send(request, HttpClientUtils.ofJson());
            if (response.statusCode() != 200) {
                throw new StatusCodeException(response.statusCode(), "/session");
            }

            tokenExpiration = System.currentTimeMillis() + MAX_TOKEN_LIFE;
            return cachedToken = response.body().getAsJsonObject().get("id").getAsString();
        } catch (InterruptedException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String toString() {
        return "Metabase@" + baseURL;
    }
}
