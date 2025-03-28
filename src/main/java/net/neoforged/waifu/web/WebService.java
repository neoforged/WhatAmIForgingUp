package net.neoforged.waifu.web;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.web.api.GraphQLWebService;
import net.neoforged.waifu.web.api.TokenManager;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class WebService {
    private final Javalin javalin;

    private final VersionWebService versionService;
    private final PlatformWebService platformService;
    private final GraphQLWebService graphQl;

    public WebService(Javalin javalin, MainDatabase db, TokenManager tokens) {
        this.javalin = javalin;

        this.javalin.get("/mod_url/<project>", ctx -> {
            var proj = ctx.pathParam("project");
            try {
                var idInt = Integer.parseInt(proj);
                var mod = Main.CF_API.getHelper().getMod(idInt).orElseThrow();
                ctx.redirect(mod.links().websiteUrl());
            } catch (NumberFormatException ignored) {
                ctx.redirect("https://modrinth.com/mod/" + proj, HttpStatus.TEMPORARY_REDIRECT);
            }
        });

        this.javalin.after(ctx -> ctx.header("Access-Control-Allow-Origin", "*"));

        this.versionService = new VersionWebService(javalin);
        this.platformService = new PlatformWebService(javalin);

        String anon = System.getenv("GRAPHQL_ANONYMOUS_RATE_LIMIT");
        boolean anonAccess = false;
        TokenManager.RateLimit anonRateLimit = null;

        if (anon != null) {
            anonAccess = true;
            if (anon.contains("/")) {
                anonRateLimit = TokenManager.RateLimit.parse(anon);
            }
        }

        this.graphQl = new GraphQLWebService(javalin, db, tokens, anonAccess, anonRateLimit);
    }

    public void start() {
        var webApiPort = System.getenv("WEB_API_PORT");
        if (webApiPort != null) {
            javalin.start(Integer.parseInt(webApiPort));
        }
    }
}
