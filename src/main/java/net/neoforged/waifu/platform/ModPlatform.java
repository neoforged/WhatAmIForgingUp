package net.neoforged.waifu.platform;

import net.neoforged.waifu.meta.ModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public interface ModPlatform {
    String MODRINTH = "modrinth";
    String CURSEFORGE = "curseforge";

    String getName();

    String getLogoUrl();

    PlatformMod getModById(Object id);

    @Nullable
    PlatformMod getModBySlug(String slug, ProjectType type);

    default Iterator<PlatformMod> searchProjects(
            String version, ModLoader loader,
            ProjectType type, SearchSortField sort
    ) {
        return searchProjects(version, loader, type, sort, null);
    }

    Iterator<PlatformMod> searchProjects(
            String version, ModLoader loader,
            ProjectType type, SearchSortField sort,
            @Nullable String searchQuery
    );

    List<PlatformModFile> getFiles(List<Object> fileIds);

    List<PlatformModFile> getModsInPack(PlatformModFile pack);

    List<@Nullable PlatformModFile> getFilesByFingerprint(List<ModFileInfo> files);

    int pageLimit();

    default void bulkFillData(List<PlatformModFile> files) {

    }

    enum SearchSortField {
        LAST_UPDATED,
        NEWEST_RELEASED,
        POPULARITY
    }

    enum ProjectType {
        MOD,
        MODPACK
    }
}
