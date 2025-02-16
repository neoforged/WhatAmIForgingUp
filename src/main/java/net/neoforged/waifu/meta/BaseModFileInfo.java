package net.neoforged.waifu.meta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.util.Hashing;
import net.neoforged.waifu.util.Utils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;

abstract class BaseModFileInfo implements ModFileInfo {
    private static final Path JIJ_CACHE = Main.CACHE.resolve("jij");

    private final ModFilePath path;
    private final Manifest manifest;
    private final List<NestedJar> nestedJars;

    BaseModFileInfo(ModFilePath path, Manifest manifest, ModFileReader reader) throws IOException {
        this.path = path;
        this.manifest = manifest;

        this.nestedJars = readNestedJars(reader);
    }

    @Override
    public List<NestedJar> getNestedJars() {
        return nestedJars;
    }

    @Override
    public Path getPath(String path) {
        return this.path.resolve(path);
    }

    @Override
    public Path getRootDirectory() {
        return path.rootDirectory();
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public void close() throws IOException {
        for (NestedJar nestedJar : nestedJars) {
            nestedJar.info().close();
        }

        path.close();
    }

    @Override
    public InputStream openStream() throws IOException {
        return Files.newInputStream(path.physicalLocation());
    }

    @Override
    public String getFileHash() {
        return path.hash();
    }

    @Override
    public long computeMurmur2() throws IOException {
        return path.computeMurmur2();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + getDisplayName() + ", path=" + path + "]";
    }

    private List<NestedJar> readNestedJars(ModFileReader reader) throws IOException {
        final Path path = getPath("META-INF/jarjar/metadata.json");
        if (Files.exists(path)) {
            var nested = new ArrayList<NestedJar>();

            final JsonArray array = Utils.GSON.fromJson(
                    Files.newBufferedReader(path), JsonObject.class
            ).getAsJsonArray("jars");

            for (final JsonElement element : array) {
                final JsonObject obj = (JsonObject) element;
                final JsonObject identifier = obj.getAsJsonObject("identifier");

                final String id = identifier.get("group").getAsString() + ":" + identifier.get("artifact").getAsString();
                final String version = obj.getAsJsonObject("version").get("artifactVersion").getAsString();

                var jarPath = getPath(obj.get("path").getAsString());
                var fileHash = Hashing.sha1().putFile(jarPath).hash();

                var hash = Hashing.sha1()
                        .putString(this.path.hash())
                        .putString(id)
                        .putString(version)
                        .putString(fileHash)
                        .hash();

                var newPath = JIJ_CACHE.resolve(hash);
                Files.createDirectories(newPath.getParent());
                try {
                    Files.copy(jarPath, newPath);
                } catch (FileAlreadyExistsException ignored) {

                }

                var jar = reader.read(new ModFilePath(
                        newPath, FileSystems.newFileSystem(newPath).getRootDirectories().iterator().next(),
                        fileHash, newPath
                ), id, version);
                if (jar != null) {
                    nested.add(new NestedJar(id, new DefaultArtifactVersion(version), jar));
                }
            }

            return nested;
        }
        return List.of();
    }
}
