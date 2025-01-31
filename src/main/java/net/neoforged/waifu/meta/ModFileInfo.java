package net.neoforged.waifu.meta;

import net.neoforged.waifu.util.Utils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public interface ModFileInfo {
    String getDisplayName();

    @Nullable
    String getMavenCoordinates();

    DefaultArtifactVersion getVersion();

    List<ModInfo> getMods();

    List<NestedJar> getNestedJars();

    Type getType();

    Path getPath(String path);

    Path getRootDirectory();

    String getFileHash();

    @Nullable ModFileMetadata getMetadata();

    void close() throws IOException;

    // see https://github.com/neoforged/FancyModLoader/blob/a4927491af05437e5cbcc14aa9b19cc238d70ed7/loader/src/main/java/net/neoforged/fml/loading/TransformerDiscovererConstants.java#L20
    List<String> SERVICES = List.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "net.neoforged.neoforgespi.locating.IModFileCandidateLocator",
            "net.neoforged.neoforgespi.locating.IModFileReader",
            "net.neoforged.neoforgespi.locating.IDependencyLocator",
            "net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider"
    );

    @Nullable
    static ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException {
        var modsToml = path.resolve("META-INF/neoforge.mods.toml");
        var man = readManifest(path.resolve("META-INF/MANIFEST.MF"));
        var version = man.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (version == null) version = versionFallback;
        if (version == null) version = "0.0NONE";

        if (Files.exists(modsToml)) {
            try (var reader = Files.newBufferedReader(modsToml)) {
                var toml = Utils.TOML.parse(reader);
                return new MetadataPoweredModFileInfo(path, version, toml, coordinates);
            }
        } else {
            var attr = man.getMainAttributes().getValue("FMLModType");
            outer: if (attr == null) {
                for (String service : SERVICES) {
                    if (Files.isRegularFile(path.resolve("META-INF/services/" + service))) {
                        attr = "LIBRARY";
                        break outer;
                    }
                }
                path.close();
                return null;
            }
            return new LibraryModFileInfo(path, Type.get(attr), version, coordinates);
        }
    }

    private static Manifest readManifest(Path manPath) throws IOException {
        var man = new Manifest();
        try (var is = Files.newInputStream(manPath)) {
            man.read(is);
        } catch (NoSuchFileException ignored) {
        }
        return man;
    }

    enum Type {
        MOD,
        LIBRARY,
        GAMELIBRARY;

        public static Type get(String value) {
            try {
                return Type.valueOf(value);
            } catch (IllegalArgumentException ex) {
                return Type.GAMELIBRARY;
            }
        }
    }

    record NestedJar(
            String identifier,
            DefaultArtifactVersion version,
            ModFileInfo info
    ) {}
}
