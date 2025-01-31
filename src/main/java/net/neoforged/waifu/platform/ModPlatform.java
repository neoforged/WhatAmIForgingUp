package net.neoforged.waifu.platform;

import java.util.Iterator;
import java.util.List;

public interface ModPlatform {
    String getName();

    PlatformMod getModById(Object id);

    Iterator<PlatformMod> searchMods(String version);

    List<PlatformModFile> getFiles(List<Object> fileIds);

    List<PlatformModFile> getModsInPack(PlatformModFile pack);
}
