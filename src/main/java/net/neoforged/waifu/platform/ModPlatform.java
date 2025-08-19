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

    PlatformProject getProjectById(Object id);

    @Nullable
    PlatformProject getProjectBySlug(String slug, ProjectType type);

    default Iterator<PlatformProject> searchProjects(
            String version, ModLoader loader,
            ProjectType type, SearchSortField sort
    ) {
        return searchProjects(version, loader, type, sort, null);
    }

    Iterator<PlatformProject> searchProjects(
            String version, ModLoader loader,
            ProjectType type, SearchSortField sort,
            @Nullable String searchQuery
    );

    List<PlatformProjectFile> getFiles(List<Object> fileIds);

    List<PlatformProjectFile> getModsInPack(PlatformProjectFile pack);

    List<@Nullable PlatformProjectFile> getFilesByFingerprint(List<ModFileInfo> files);

    int pageLimit();

    default void bulkFillFiles(List<PlatformProjectFile> files) {

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
