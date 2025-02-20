package net.neoforged.waifu.meta;

import io.github.matyrobbrt.curseforgeapi.util.Pair;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Manifest;

abstract class BaseModFileInfo implements ModFileInfo {
    public static final Path JIJ_CACHE = Main.CACHE.resolve("jij");

    private final ModFileReader reader;

    private final ModFilePath path;
    private final Manifest manifest;
    private final List<NestedJar> nestedJars;

    BaseModFileInfo(ModFilePath path, Manifest manifest, ModFileReader reader) throws IOException {
        this.reader = reader;
        this.path = path;
        this.manifest = manifest;

        this.nestedJars = reader.readNestedJars(this);
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
    public Pair<String, String> getModMetadata() throws Exception {
        var text = Files.readString(getPath(reader.getMetadataFileName()), StandardCharsets.UTF_8);
        if (reader.getMetadataFileName().endsWith(".toml")) {
            return Pair.of(text, Utils.tomlToJson(text));
        } else if (reader.getMetadataFileName().endsWith(".json")) {
            return Pair.of(text, text);
        }
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + getDisplayName() + ", path=" + path + "]";
    }

}
