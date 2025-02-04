package net.neoforged.waifu.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;

public interface PlatformMod {
    Object getId();

    String getSlug();

    @Nullable
    PlatformModFile getLatestFile(String gameVersion);

    Iterator<PlatformModFile> getAllFiles();

    Instant getLatestReleaseDate();

    default boolean isAvailable() {
        return true;
    }
}
