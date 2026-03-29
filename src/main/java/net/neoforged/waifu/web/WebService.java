package net.neoforged.waifu.web;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.web.api.GraphQLWebService;
import net.neoforged.waifu.web.api.OAuthClient;
import net.neoforged.waifu.web.api.TokenManager;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class WebService {
    private final Javalin javalin;

    private GraphQLWebService graphQl;

    public WebService(Consumer<JavalinConfig> consumer, MainDatabase db, TokenManager tokens) {
        this.javalin = Javalin.create(config -> {
            consumer.accept(config);

            var routes = config.routes;
            routes.get("/api/mod_url/<project>", ctx -> {
                var proj = ctx.pathParam("project");
                try {
                    var idInt = Integer.parseInt(proj);
                    var mod = Main.CURSE_FORGE_API.getHelper().getMod(idInt).orElseThrow();
                    ctx.redirect(mod.links().websiteUrl());
                } catch (NumberFormatException ignored) {
                    ctx.redirect("https://modrinth.com/mod/" + proj, HttpStatus.TEMPORARY_REDIRECT);
                }
            });

            routes.get("/api/indexed-versions", ctx -> ctx.json(Main.DB_MANAGER.getAllVersions().stream()
                    .map(v -> Map.of("gameVersion", v.gameVersion(), "loader", v.loader().getDisplayName())).toList()));

            OAuthClient discordOAuthClient = new OAuthClient(
                    "https://discord.com/oauth2/authorize", "https://discord.com/api/oauth2/token",
                    System.getenv("DISCORD_OAUTH_CLIENT_ID"), System.getenv("DISCORD_OAUTH_CLIENT_SECRET"),
                    withTrailingSlash(System.getenv().getOrDefault("WEB_SERVER_URL", "")) + "oauth2/discord",
                    Main.HTTP_CLIENT, "identify"
            );
            routes.get("/oauth2/discord", ctx -> verifyDiscord(ctx, discordOAuthClient));

            String anon = System.getenv("GRAPHQL_ANONYMOUS_RATE_LIMIT");
            boolean anonAccess = false;
            TokenManager.RateLimit anonRateLimit = null;

            if (anon != null) {
                anonAccess = true;
                if (anon.contains("/")) {
                    anonRateLimit = TokenManager.RateLimit.parse(anon);
                }
            }

            TokenManager.RateLimit discordRateLimit = System.getenv("GRAPHQL_DISCORD_RATE_LIMIT") == null ? null : TokenManager.RateLimit.parse(System.getenv("GRAPHQL_DISCORD_RATE_LIMIT"));

            this.graphQl = new GraphQLWebService(routes, db, tokens, anonAccess, anonRateLimit, discordRateLimit, getDefaultTimeout());
        });
    }

    public void start() {
        var webApiPort = System.getenv("WEB_SERVER_PORT");
        if (webApiPort != null) {
            javalin.start(Integer.parseInt(webApiPort));
        }
    }

    private void verifyDiscord(Context ctx, OAuthClient client) throws IOException, InterruptedException {
        if (ctx.queryParam("code") == null) {
            ctx.redirect(client.getAuthorizationUrl(), HttpStatus.TEMPORARY_REDIRECT);
        } else {
            final var token = client.getToken(ctx.queryParam("code"));
            final var devWebInterfaceUrl = System.getenv("DEV_WEB_INTERFACE_URL");

            if (devWebInterfaceUrl == null) {
                ctx.cookie(new Cookie(
                        "discord-token",
                        token.accessToken(),
                        "/",
                        (int) (token.expiration().getEpochSecond() - Instant.now().getEpochSecond()),
                        false
                ));
                ctx.redirect("/oauth2/discord/completed", HttpStatus.TEMPORARY_REDIRECT);
            } else {
                ctx.redirect(withTrailingSlash(devWebInterfaceUrl) + "oauth2/discord/completed?token=" + token.accessToken(), HttpStatus.TEMPORARY_REDIRECT);
            }
        }
    }

    private String withTrailingSlash(String url) {
        return url.endsWith("/") ? url : (url + "/");
    }

    public static int getDefaultTimeout() {
        return Integer.parseInt(System.getenv().getOrDefault("GRAPHQL_DEFAULT_TIMEOUT", "30"));
    }
}
