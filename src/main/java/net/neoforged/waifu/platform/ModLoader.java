package net.neoforged.waifu.platform;

import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFileReader;
import net.neoforged.waifu.util.NeoForgeJarProvider;
import net.neoforged.waifu.util.ThrowingFunction;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public enum ModLoader {
    NEOFORGE("https://github.com/neoforged.png", new VersionProvider(
            "net.neoforged:neoforge",
            NeoForgeJarProvider::getLatestVersion,
            NeoForgeJarProvider::provide
    ), ModFileReader.NEOFORGE),
    
    // TODO - forge jar provider. Will be a bit of a pain because of tool mismatches
    FORGE("https://github.com/minecraftforge.png", null, ModFileReader.FORGE);

    private final String logo;
    @Nullable
    private final VersionProvider versionProvider;
    private final ModFileReader reader;

    ModLoader(String logo, @Nullable VersionProvider versionProvider, ModFileReader reader) {
        this.logo = logo;
        this.versionProvider = versionProvider;
        this.reader = reader;
    }

    public String getLogoUrl() {
        return logo;
    }

    @Nullable
    public VersionProvider getVersionProvider() {
        return versionProvider;
    }

    public ModFileReader getReader() {
        return reader;
    }

    public record VersionProvider(
            String artifactName,
            Function<String, String> latestVersion,
            ThrowingFunction<String, List<ModFileInfo>, IOException> jarProvider
    ) {}
}
