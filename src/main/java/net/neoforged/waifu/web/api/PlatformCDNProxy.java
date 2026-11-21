package net.neoforged.waifu.web.api;

import io.javalin.http.Context;
import net.neoforged.waifu.Main;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A proxy for CurseForge/Modrinth CDN proxy because, while Modrinth's CDN does allow CORS, CurseForge's does not.
 */
public class PlatformCDNProxy {
    static void proxy(Context ctx) throws IOException {
        String url = ctx.queryParam("url");
        if (url == null) {
            ctx.status(400).result("Missing 'url' query parameter");
            return;
        }
        if (!url.startsWith("https://")) {
            ctx.status(400).result("Only https allowed");
            return;
        }

        URI uri;
        try {
            uri = URI.create(encodePath(url));
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("Invalid URL");
            return;
        }

        String host = uri.getHost();
        if (host == null || !(host.equals("edge.forgecdn.net") || host.equals("cdn.modrinth.com"))) {
            ctx.status(403).result("Domain not allowed");
            return;
        }

        URL target = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);

        // See https://blog.curseforge.com/introducing-api-key-authentication-for-curseforge-file-downloads/
        if (host.equals("edge.forgecdn.net")) {
            conn.setRequestProperty("x-api-key", Main.CURSE_FORGE_API.getApiKey());
        }

        int code = conn.getResponseCode();
        if (code >= 400) {
            ctx.status(code).result("Upstream error: " + code);
            return;
        }

        String contentType = conn.getContentType();
        if (contentType != null) {
            ctx.contentType(contentType);
        }

        InputStream in = conn.getInputStream();
        ctx.result(in);
    }

    private static String encodePath(String rawUrl) {
        int idx = rawUrl.indexOf('/', rawUrl.indexOf("//") + 2);

        String base = rawUrl.substring(0, idx);
        String path = rawUrl.substring(idx);

        String encodedPath = Arrays.stream(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");

        return base + encodedPath;
    }
}
