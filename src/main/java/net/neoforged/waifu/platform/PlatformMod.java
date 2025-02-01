package net.neoforged.waifu.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public interface PlatformMod {
    Object getId();

    String getSlug();

    @Nullable
    PlatformModFile getLatestFile(String gameVersion);

    Instant getLatestReleaseDate();
}
