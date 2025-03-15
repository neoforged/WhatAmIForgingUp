package net.neoforged.waifu.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;

public interface PlatformMod {
    ModPlatform getPlatform();

    Object getId();

    String getSlug();

    String getTitle();

    String getDescription();

    String getIconUrl();

    long getDownloads();

    Instant getReleasedDate();

    @Nullable
    PlatformModFile getLatestFile(String gameVersion, @Nullable ModLoader loader);

    Iterator<PlatformModFile> getAllFiles();

    Iterator<PlatformModFile> getFilesForVersion(String version, ModLoader loader);

    Instant getLatestReleaseDate();

    default boolean isAvailable() {
        return true;
    }
}
