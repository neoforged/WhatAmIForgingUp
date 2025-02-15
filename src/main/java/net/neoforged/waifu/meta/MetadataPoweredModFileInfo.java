package net.neoforged.waifu.meta;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.jar.Manifest;

class MetadataPoweredModFileInfo extends BaseModFileInfo {
    private final List<ModInfo> mods;
    private final DefaultArtifactVersion version;
    private final ModFileMetadata metadata;

    @Nullable
    private final String coordinates;
    MetadataPoweredModFileInfo(ModFilePath path, Manifest man, String jarVersion, Config toml, String coordinates) throws IOException  {
        super(path, man);
        this.coordinates = coordinates;

        this.metadata = new ModFileMetadata(
                toml.getOrElse("license", "All Rights Reserved"),
                toml.get("issueTrackerURL"),
                toml.get("modLoader")
        );

        this.mods = toml.<List<CommentedConfig>>get("mods")
                .stream()
                .map(cfg -> ModInfo.create(cfg, jarVersion))
                .toList();

        this.version = mods.isEmpty() ? new DefaultArtifactVersion(Objects.requireNonNullElse(jarVersion, "0.0NONE")) : mods.get(0).version();
    }

    @Override
    public @Nullable String getMavenCoordinates() {
        return coordinates;
    }

    @Override
    public String getDisplayName() {
        return mods.isEmpty() ? "" : mods.get(0).displayName();
    }

    @Override
    public DefaultArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<ModInfo> getMods() {
        return mods;
    }

    @Override
    public Type getType() {
        return Type.MOD;
    }

    @Override
    public @Nullable ModFileMetadata getMetadata() {
        return metadata;
    }
}
