package net.neoforged.waifu.meta;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.util.Hashing;
import net.neoforged.waifu.util.Utils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public interface ModFileReader {
    // see https://github.com/neoforged/FancyModLoader/blob/a4927491af05437e5cbcc14aa9b19cc238d70ed7/loader/src/main/java/net/neoforged/fml/loading/TransformerDiscovererConstants.java#L20
    ModFileReader NEOFORGE = new ForgeLikeReader("META-INF/neoforge.mods.toml", List.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "net.neoforged.neoforgespi.locating.IModFileCandidateLocator",
            "net.neoforged.neoforgespi.locating.IModFileReader",
            "net.neoforged.neoforgespi.locating.IDependencyLocator",
            "net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider"
    ));

    // see https://github.com/MinecraftForge/MinecraftForge/blob/6ae39c859714b28aea94169d2a6abadc4993d274/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModDirTransformerDiscoverer.java#L29-L32
    ModFileReader FORGE = new ForgeLikeReader("META-INF/mods.toml", List.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "net.minecraftforge.forgespi.locating.IModLocator",
            "net.minecraftforge.forgespi.locating.IDependencyLocator"
    ));

    @Nullable
    ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException;

    String getMetadataFileName();

    List<ModFileInfo.NestedJar> readNestedJars(ModFileInfo rootFile) throws IOException;

    private static Manifest readManifest(Path manPath) throws IOException {
        var man = new Manifest();
        try (var is = Files.newInputStream(manPath)) {
            man.read(is);
        } catch (NoSuchFileException ignored) {
        }
        return man;
    }

    class ForgeLikeReader implements ModFileReader {
        private final String metadataFileName;
        private final List<String> services;

        ForgeLikeReader(String metadataFileName, List<String> services) {
            this.metadataFileName = metadataFileName;
            this.services = services;
        }

        @Override
        public @Nullable ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException {
            var modsToml = path.resolve(metadataFileName);
            var man = readManifest(path.resolve("META-INF/MANIFEST.MF"));
            var version = man.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (version == null) version = versionFallback;
            if (version == null) version = "0.0NONE";

            if (Files.exists(modsToml)) {
                try (var reader = Files.newBufferedReader(modsToml)) {
                    CommentedConfig toml = null;
                    try {
                        toml = Utils.TOML.parse(reader); // invalid TOML is invalid
                    } catch (Exception ignored) {
                        Main.LOGGER.error("File at {} has invalid TOML", path.physicalLocation());
                    }

                    if (toml != null) {
                        var mods = toml.<List<CommentedConfig>>get("mods");
                        if (mods != null && !mods.isEmpty()) {
                            return new MetadataPoweredModFileInfo(path, man, this, version, toml, coordinates);
                        }
                    }
                }
            }

            var attr = man.getMainAttributes().getValue("FMLModType");
            outer: if (attr == null) {
                for (String service : services) {
                    if (Files.isRegularFile(path.resolve("META-INF/services/" + service))) {
                        attr = "LIBRARY";
                        break outer;
                    }
                }
                path.close();
                return null;
            }
            return new LibraryModFileInfo(path, man, this, ModFileInfo.Type.get(attr), version, coordinates);
        }

        @Override
        public String getMetadataFileName() {
            return metadataFileName;
        }

        @Override
        public List<ModFileInfo.NestedJar> readNestedJars(ModFileInfo rootFile) throws IOException {
            final Path path = rootFile.getPath("META-INF/jarjar/metadata.json");
            if (Files.exists(path)) {
                var nested = new ArrayList<ModFileInfo.NestedJar>();

                final JsonArray array = Utils.GSON.fromJson(
                        Files.newBufferedReader(path), JsonObject.class
                ).getAsJsonArray("jars");

                for (final JsonElement element : array) {
                    final JsonObject obj = (JsonObject) element;
                    final JsonObject identifier = obj.getAsJsonObject("identifier");

                    final String id = identifier.get("group").getAsString() + ":" + identifier.get("artifact").getAsString();
                    final String version = obj.getAsJsonObject("version").get("artifactVersion").getAsString();

                    var jarPath = rootFile.getPath(obj.get("path").getAsString());
                    var fileHash = Hashing.sha1().putFile(jarPath).hash();

                    var hash = Hashing.sha1()
                            .putString(rootFile.getFileHash())
                            .putString(id)
                            .putString(version)
                            .putString(fileHash)
                            .hash();

                    var newPath = BaseModFileInfo.JIJ_CACHE.resolve(hash);
                    Files.createDirectories(newPath.getParent());
                    try {
                        Files.copy(jarPath, newPath);
                    } catch (FileAlreadyExistsException ignored) {

                    }

                    var jar = read(new ModFilePath(
                            newPath, FileSystems.newFileSystem(newPath).getRootDirectories().iterator().next(),
                            fileHash, newPath
                    ), id, version);
                    if (jar != null) {
                        nested.add(new ModFileInfo.NestedJar(id, new DefaultArtifactVersion(version), jar));
                    }
                }

                return nested;
            }
            return List.of();
        }
    }

}
