package net.neoforged.waifu.platform;

import net.neoforged.waifu.util.ModLoader;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;

public interface PlatformMod {
    Object getId();

    String getSlug();

    @Nullable
    PlatformModFile getLatestFile(String gameVersion, ModLoader loader);

    Iterator<PlatformModFile> getAllFiles();

    Iterator<PlatformModFile> getFilesForVersion(String version, ModLoader loader);

    Instant getLatestReleaseDate();

    default boolean isAvailable() {
        return true;
    }
}
