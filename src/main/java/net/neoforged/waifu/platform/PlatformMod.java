package net.neoforged.waifu.platform;

public interface PlatformMod {
    Object getId();

    String getSlug();

    PlatformModFile getLatestFile(String gameVersion);
}
