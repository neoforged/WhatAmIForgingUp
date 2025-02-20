package net.neoforged.waifu.platform;

import net.neoforged.srgutils.IMappingFile;
import net.neoforged.waifu.index.Remapper;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFileReader;
import net.neoforged.waifu.util.MinecraftJarProvider;
import net.neoforged.waifu.util.MinecraftMetaUtils;
import net.neoforged.waifu.util.NeoForgeJarProvider;
import net.neoforged.waifu.util.ThrowingFunction;
import net.neoforged.waifu.util.Utils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public enum ModLoader {
    NEOFORGE("https://github.com/neoforged.png", new VersionProvider(
            "net.neoforged:neoforge",
            NeoForgeJarProvider::getLatestVersion,
            NeoForgeJarProvider::provide
    ), ModFileReader.NEOFORGE),

    // TODO - forge jar provider. Will be a bit of a pain because of tool mismatches
    FORGE("https://github.com/minecraftforge.png", null, ModFileReader.FORGE) {
        private static final ArtifactVersion MC_1_20_6 = new DefaultArtifactVersion("1.20.6");
        private static final String MCP_CONFIG_URL =
                "https://maven.neoforged.net/releases/de/oceanlabs/mcp/mcp_config/%s/mcp_config-%<s.zip";

        @Override
        public Remapper createRemapper(String gameVersion) throws IOException {
            var version = new DefaultArtifactVersion(gameVersion);
            // Forge versions prior to 1.20.6 use SRG mappings
            if (version.compareTo(MC_1_20_6) < 0) {
                var joined = Utils.readFromZip(URI.create(MCP_CONFIG_URL.formatted(gameVersion)), "config/joined.tsrg", IMappingFile::load);

                var namedToObf = Utils.read(
                        MinecraftMetaUtils.getVersion(gameVersion).get()
                                .download("client_mappings").url(),
                        IMappingFile::load
                );

                var srgToNamed = namedToObf.chain(joined).reverse();
                // This number is based on 1.20.1 data
                var methods = new HashMap<String, String>(30_000);
                var fields = new HashMap<String, String>(30_000);

                for (IMappingFile.IClass cls : srgToNamed.getClasses()) {
                    for (IMappingFile.IMethod method : cls.getMethods()) {
                        if (method.getOriginal().startsWith("m_")) {
                            methods.put(method.getOriginal(), method.getMapped());
                        }
                    }
                    for (IMappingFile.IField field : cls.getFields()) {
                        if (field.getOriginal().startsWith("f_")) {
                            fields.put(field.getOriginal(), field.getMapped());
                        }
                    }
                }

                return new Remapper.DumbPrefixedId(
                        null, Map.of(),
                        "m_", methods,
                        "f_", fields
                );
            }

            return Remapper.NOOP;
        }
    },

    FABRIC("https://github.com/fabricmc.png", new VersionProvider(
            "net.minecraft:minecraft",
            Function.identity(), // Fabric only needs to process vanilla Minecraft
            MinecraftJarProvider::provide
    ), ModFileReader.FABRIC) {
        private static final String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/%s/intermediary-%<s-v2.jar";

        @Override
        public Remapper createRemapper(String gameVersion) throws IOException {
            var obfToInter = Utils.readFromZip(URI.create(INTERMEDIARY_URL.formatted(gameVersion)), "mappings/mappings.tiny", IMappingFile::load);

            var namedToObf = Utils.read(
                    MinecraftMetaUtils.getVersion(gameVersion).get().download("client_mappings").url(),
                    IMappingFile::load
            );

            var interToNamed = namedToObf.chain(obfToInter).reverse();

            var classes = HashMap.<String, String>newHashMap(interToNamed.getClasses().size());
            // This number is based on 1.20.1 data
            var methods = new HashMap<String, String>(30_000);
            var fields = new HashMap<String, String>(30_000);

            for (IMappingFile.IClass cls : interToNamed.getClasses()) {
                classes.put(cls.getOriginal(), cls.getMapped());
                for (IMappingFile.IMethod method : cls.getMethods()) {
                    methods.put(method.getOriginal(), method.getMapped());
                }
                for (IMappingFile.IField field : cls.getFields()) {
                    fields.put(field.getOriginal(), field.getMapped());
                }
            }

            return new Remapper.DumbPrefixedId(
                    "net/minecraft/class_", classes,
                    "method_", methods,
                    "field_", fields
            );
        }
    };

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

    public Remapper createRemapper(String gameVersion) throws IOException {
        return Remapper.NOOP;
    }

    public record VersionProvider(
            String artifactName,
            Function<String, String> latestVersion,
            ThrowingFunction<String, List<ModFileInfo>, IOException> jarProvider
    ) {}
}
