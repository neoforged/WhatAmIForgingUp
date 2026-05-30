package net.neoforged.waifu.web.api;

import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

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

        URI uri;
        try {
            uri = URI.create(url);
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
}
