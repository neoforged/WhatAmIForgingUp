package net.neoforged.waifu.web.api;

import com.google.gson.JsonObject;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.util.Utils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A client for handling OAuth web authorization.
 */
public class OAuthClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthClient.class);

    private final String authorizeUrl, tokenUrl;
    private final String clientId, clientSecret;
    private final String redirectUri;
    private final Set<String> scopes;
    private final HttpClient httpClient;

    public OAuthClient(String authorizeUrl, String tokenUrl, String clientId, String clientSecret, String redirectUri, HttpClient httpClient, Object... scopes) {
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scopes = Arrays.stream(scopes).map(Object::toString).collect(Collectors.toUnmodifiableSet());
        this.httpClient = httpClient;
    }

    public TokenResponse getToken(String code) throws IOException, InterruptedException {
        final Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("scope", String.join(" ", scopes));
        final Instant requestedAt = Instant.now();
        final var response = httpClient.send(HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(params.entrySet().stream()
                        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"))))
                .build(), jsonObject());
        final JsonObject responseBody = response.body();
        if (response.statusCode() != HttpStatus.OK.getCode()) {
            LOGGER.error("OAuth token request returned non-200 ({}) status code, with error '{}' ({}): {}", response.statusCode(),
                    responseBody.get("error").getAsString(), responseBody.get("error_description").getAsString(), responseBody);
            throw new IOException("OAuth token request failed: " + responseBody.get("error").getAsString());
        }

        final String token = responseBody.get("access_token").getAsString();
        final long expiresIn = responseBody.get("expires_in").getAsLong();
        return new TokenResponse(token, requestedAt.plusSeconds(expiresIn));
    }

    public String getAuthorizationUrl(@Nullable String state) {
        final String url = authorizeUrl + "?client_id=" + clientId + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) + "&response_type=code&scope=" + String.join("%20", scopes);
        return state == null ? url : (url + "&state=" + state);
    }

    public String getAuthorizationUrl() {
        return getAuthorizationUrl(null);
    }

    private static HttpResponse.BodyHandler<JsonObject> jsonObject() {
        return responseInfo -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), str -> Utils.GSON.fromJson(str, JsonObject.class));
    }

    /**
     * A token returned by the OAuth flow.
     *
     * @param accessToken the token
     * @param expiration the time of expiration of the tokens
     */
    public record TokenResponse(
            String accessToken,
            Instant expiration
    ) {
    }
}
