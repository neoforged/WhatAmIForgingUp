package net.neoforged.waifu.web;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;

public class WebService {
    private final Javalin javalin;

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final VersionWebService versionService;

    public WebService(Javalin javalin) {
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
    }

    public void start() {
        var webApiPort = System.getenv("WEB_API_PORT");
        if (webApiPort != null) {
            javalin.start(Integer.parseInt(webApiPort));
        }
    }
}
