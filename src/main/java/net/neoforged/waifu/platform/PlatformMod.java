package net.neoforged.waifu.platform;

import java.time.Instant;

public interface PlatformMod {
    Object getId();

    String getSlug();

    PlatformModFile getLatestFile(String gameVersion);

    Instant getLatestReleaseDate();
}
