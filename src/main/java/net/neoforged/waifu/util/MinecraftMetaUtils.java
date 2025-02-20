package net.neoforged.waifu.util;

import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class MinecraftMetaUtils {
    private static final URI MANIFEST =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    public static List<Version> getVersions() {
        record Man(List<Version> versions) {}
        return Utils.getJson(MANIFEST, Man.class).versions();
    }

    public static Version getVersion(String version) {
        return getVersions().stream()
                .filter(v -> v.id.equals(version))
                .findFirst()
                .orElseThrow();
    }

    public record Version(String id, URI url) {
        public Package get() throws IOException {
            return Utils.getJson(url, Package.class);
        }
    }

    public record Package(Map<String, Download> downloads) {
        public Download download(String id) {
            return downloads.get(id);
        }
    }

    public record Download(URI url) {

    }
}
