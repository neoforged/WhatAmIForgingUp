package net.neoforged.waifu.web.api;

import com.google.gson.JsonArray;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ContentType;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.statement.StatementContext;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class InternalAPI {
    public InternalAPI(RoutesConfig config, MainDatabase db) {
        config.get("/api/internal/indexed-versions", ctx -> ctx.json(Main.DB_MANAGER.getAllVersions().stream().sorted()
                .map(v -> Map.of("gameVersion", v.gameVersion(), "loader", v.loader().getDisplayName(), "activelyIndexed", db.isBeingIndexed(v.gameVersion(), v.loader()))).toList()));

        config.get("/api/internal/github-refs/{owner}/{repo}", ctx -> {
            var response = Main.HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("https://github.com/" + ctx.pathParam("owner")
                    + "/" + ctx.pathParam("repo") + ".git/info/refs?service=git-upload-pack")).build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                ctx.status(response.statusCode()).result(response.body().collect(Collectors.joining("\n")));
                return;
            }

            var array = new JsonArray();
            response.body().forEach(line -> {
                var split = line.split(" ");
                if (split.length == 2 && split[1].startsWith("refs/heads/")) {
                    array.add(split[1]);
                }
            });

            ctx.result(Utils.GSON.toJson(array)).contentType(ContentType.APPLICATION_JSON);
        });

        config.get("/api/internal/download-github/{owner}/{repo}/<ref>", ctx -> {
            var response = Main.HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("https://github.com/" + ctx.pathParam("owner")
                    + "/" + ctx.pathParam("repo") + "/archive/" + ctx.pathParam("ref") + ".zip")).build(), HttpResponse.BodyHandlers.ofInputStream());
            ctx.status(response.statusCode()).result(response.body());
        });

        config.get("/api/internal/autocomplete/class", ctx -> {
            var jdbi = Main.DB_MANAGER.setupReadOnlyConnection(
                    Objects.requireNonNull(ctx.queryParam("version")),
                    GraphQLWebService.getLoader(Objects.requireNonNull(ctx.queryParam("loader")))
            );
            var currentQuery = ctx.queryParam("query");

            ctx.json(jdbi.withHandle(handle -> handle.createQuery("select classes.name from classes where classes.name like ? limit 50")
                    .bind(0, "%" + currentQuery + "%")
                    .execute(this::collectAsList)));
        });

        config.get("/api/internal/autocomplete/method", ctx -> {
            var jdbi = Main.DB_MANAGER.setupReadOnlyConnection(
                    Objects.requireNonNull(ctx.queryParam("version")),
                    GraphQLWebService.getLoader(Objects.requireNonNull(ctx.queryParam("loader")))
            );

            var clazz = ctx.queryParam("class");
            var currentQuery = ctx.queryParam("query");

            ctx.json(jdbi.withHandle(handle -> handle.createQuery("select constants.constant from methods join classes on classes.name = ? and methods.cls = classes.id join constants on methods.name = constants.id and constants.constant like ?")
                    .bind(0, clazz)
                    .bind(1, "%" + currentQuery + "%")
                    .execute(this::collectAsList)));
        });

        config.get("/api/internal/cdn-proxy", PlatformCDNProxy::proxy);
    }

    private List<String> collectAsList(Supplier<PreparedStatement> statementSupplier, StatementContext ctx) throws SQLException {
        var rs = statementSupplier.get().getResultSet();
        var results = new ArrayList<String>();
        while (rs.next()) {
            results.add(rs.getString(1));
        }
        return results;
    }
}
