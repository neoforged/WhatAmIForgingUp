package net.neoforged.waifu.util;

import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFileReader;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class ForgeJarProvider {
    private static final String PROMOTIONS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String DOWNLOAD_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/${version}/forge-${version}-${type}.jar";

    public static String getLatestVersion(String mcVersion) {
        record Response(Map<String, String> promos) {}
        return Utils.getJson(URI.create(PROMOTIONS_URL), Response.class).promos().get(mcVersion + "-latest");
    }

    public static List<ModFileInfo> provide(String mcVersion, String forgeVersion) throws IOException {
        // https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-installer.jar
        return NeoForgeJarProvider.provide("net.minecraftforge:forge", DOWNLOAD_URL, ModFileReader.FORGE, mcVersion, mcVersion + "-" + forgeVersion, forgeVersion);
    }
}
