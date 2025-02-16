package net.neoforged.waifu.meta;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
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

    Manifest getManifest();

    String getFileHash();

    long computeMurmur2() throws IOException;

    @Nullable ModFileMetadata getMetadata();

    void close() throws IOException;

    InputStream openStream() throws IOException;

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
