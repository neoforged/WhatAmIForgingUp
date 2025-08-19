package net.neoforged.waifu.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;

public interface PlatformProject {
    ModPlatform getPlatform();

    Object getId();

    String getSlug();

    String getTitle();

    String getDescription();

    String getIconUrl();

    long getDownloads();

    Instant getReleasedDate();

    String getUrl();

    @Nullable
    PlatformProjectFile getLatestFile(String gameVersion, @Nullable ModLoader loader);

    Iterator<PlatformProjectFile> getAllFiles();

    Iterator<PlatformProjectFile> getFilesForVersion(String version, ModLoader loader);

    @Nullable
    Instant getLatestReleaseDate();

    default boolean isAvailable() {
        return true;
    }
}
