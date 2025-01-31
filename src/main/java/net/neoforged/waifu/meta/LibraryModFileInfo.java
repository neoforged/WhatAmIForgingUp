package net.neoforged.waifu.meta;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

class LibraryModFileInfo extends BaseModFileInfo implements ModFileInfo {
    private final Type type;
    private final DefaultArtifactVersion version;
    private final String coordinates;
    LibraryModFileInfo(ModFilePath path, Type type, String version, String coordinates) throws IOException {
        super(path);
        this.type = type;
        this.version = new DefaultArtifactVersion(version);
        this.coordinates = coordinates;
    }

    @Override
    public @Nullable String getMavenCoordinates() {
        return coordinates;
    }

    @Override
    public String getDisplayName() {
        return coordinates;
    }

    @Override
    public DefaultArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<ModInfo> getMods() {
        return List.of();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public @Nullable ModFileMetadata getMetadata() {
        return null;
    }
}
