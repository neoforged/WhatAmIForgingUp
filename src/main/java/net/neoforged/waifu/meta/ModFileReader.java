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
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    ModFileReader FABRIC = new FabricReader();

    ModFileReader LIBRARY = new ModFileReader() {
        @Override
        public ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException {
            var man = readManifest(path.resolve("META-INF/MANIFEST.MF"));
            return new LibraryModFileInfo(path, man, this, ModFileInfo.Type.GAMELIBRARY, versionFallback, coordinates);
        }

        @Override
        public String getMetadataFileName() {
            return null;
        }

        @Override
        public List<ModFileInfo.NestedJar> readNestedJars(ModFileInfo rootFile) throws IOException {
            return List.of();
        }
    };

    @Nullable
    ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException;

    @Nullable
    String getMetadataFileName();

    List<ModFileInfo.NestedJar> readNestedJars(ModFileInfo rootFile) throws IOException;

    private static Manifest readManifest(Path manPath) {
        var man = new Manifest();
        try (var is = Files.newInputStream(manPath)) {
            man.read(is);
        } catch (IOException ignored) {
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
                    if (Files.notExists(jarPath)) continue; // Skip JiJ'd jars that do not exist... somehow

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

                    var modPath = ModFileReader.openRoot(newPath);
                    if (modPath == null) continue;

                    var jar = read(new ModFilePath(
                            newPath, modPath,
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

    class FabricReader implements ModFileReader {
        @Override
        public String getMetadataFileName() {
            return "fabric.mod.json";
        }

        @Override
        public @Nullable ModFileInfo read(ModFilePath path, @Nullable String coordinates, @Nullable String versionFallback) throws IOException {
            var metadataFile = path.resolve(getMetadataFileName());
            var man = readManifest(path.resolve("META-INF/MANIFEST.MF"));
            var version = man.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (version == null) version = versionFallback;
            if (version == null) version = "0.0NONE";

            if (Files.exists(metadataFile)) {
                try (var reader = Files.newBufferedReader(metadataFile)) {
                    JsonObject json = null;
                    try {
                        json = Utils.GSON.fromJson(reader, JsonObject.class); // invalid JSON is invalid
                    } catch (Exception ignored) {
                        Main.LOGGER.error("File at {} has invalid JSON", path.physicalLocation());
                    }

                    if (json != null) {
                        // Adapt the Fabric configuration format to the NeoForge one to save us needing multiple parsers for the metadata

                        var cfg = CommentedConfig.inMemory();
                        if (json.has("license")) {
                            var lic = json.get("license");
                            if (lic.isJsonArray()) {
                                cfg.set("license", StreamSupport.stream(lic.getAsJsonArray().spliterator(), false)
                                        .map(JsonElement::getAsString).collect(Collectors.joining(", ")));
                            } else {
                                cfg.set("license", lic.getAsString());
                            }
                        }

                        var mod = CommentedConfig.inMemory();

                        mod.set("modId", json.get("id").getAsString());
                        mod.set("version", json.get("version").getAsString());

                        if (json.has("name")) {
                            mod.set("displayName", json.get("name").getAsString());
                        }

                        if (json.has("authors")) {
                            var auth = json.get("authors").getAsJsonArray();
                            mod.set("authors", StreamSupport.stream(auth.spliterator(), false)
                                    .map(e -> e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString())
                                    .collect(Collectors.joining(", ")));
                        }

                        cfg.set("mods", List.of(mod));

                        return new MetadataPoweredModFileInfo(path, man, this, version, cfg, coordinates);
                    }
                }
            }

            return null;
        }

        @Override
        public List<ModFileInfo.NestedJar> readNestedJars(ModFileInfo rootFile) throws IOException {
            var fmjFile = rootFile.getPath(getMetadataFileName());
            if (Files.notExists(fmjFile)) return List.of();
            try (var reader = Files.newBufferedReader(fmjFile)) {
                var fmj = Utils.GSON.fromJson(reader, JsonObject.class);
                if (fmj.has("jars")) {
                    var jars = fmj.get("jars").getAsJsonArray();
                    var nested = new ArrayList<ModFileInfo.NestedJar>(jars.size());
                    for (JsonElement jar : jars) {
                        var path = rootFile.getPath(jar.getAsJsonObject().get("file").getAsString());
                        if (Files.notExists(path)) continue; // Skip JiJ'd jars that do not exist... somehow

                        var fileHash = Hashing.sha1().putFile(path).hash();

                        var hash = Hashing.sha1()
                                .putString(rootFile.getFileHash())
                                .putString(fileHash)
                                .hash();

                        var newPath = BaseModFileInfo.JIJ_CACHE.resolve(hash);
                        Files.createDirectories(newPath.getParent());
                        try {
                            Files.copy(path, newPath);
                        } catch (FileAlreadyExistsException ignored) {

                        }

                        var modRoot = ModFileReader.openRoot(newPath);
                        if (modRoot == null) continue;

                        var subFmj = modRoot.resolve(getMetadataFileName());
                        if (Files.exists(subFmj)) {
                            try (var subReader = Files.newBufferedReader(subFmj)) {
                                var json = Utils.GSON.fromJson(subReader, JsonObject.class);
                                var id = json.get("id").getAsString(); // This is not actually a maven coordinate but oh well... Fabric JiJ is just a list of jars to include
                                var version = json.get("version").getAsString();

                                var subJar = read(new ModFilePath(
                                        newPath, modRoot, fileHash, newPath
                                ), id, version);

                                if (subJar != null) {
                                    nested.add(new ModFileInfo.NestedJar(
                                            id, new DefaultArtifactVersion(version), subJar
                                    ));
                                }
                            }
                        }
                    }

                    return nested;
                }
            }

            return List.of();
        }
    }

    @Nullable
    private static Path openRoot(Path jar) throws IOException {
        try {
            return FileSystems.newFileSystem(jar).getRootDirectories().iterator().next();
        } catch (ProviderNotFoundException ex) {
            return null;
        }
    }
}
