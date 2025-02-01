package net.neoforged.waifu.platform;

import net.neoforged.waifu.meta.ModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public interface ModPlatform {
    String getName();

    String getLogoUrl();

    PlatformMod getModById(Object id);

    Iterator<PlatformMod> searchMods(String version);

    List<PlatformModFile> getFiles(List<Object> fileIds);

    List<PlatformModFile> getModsInPack(PlatformModFile pack);

    List<@Nullable PlatformModFile> getFilesByFingerprint(List<ModFileInfo> files);

    default void bulkFillData(List<PlatformModFile> files) {

    }
}
