package net.neoforged.waifu.db;

import com.google.common.collect.Multimap;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformMod;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.ThrowingConsumer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface IndexDatabase<T extends IndexDatabase.DatabaseMod<T>> extends AutoCloseable {
    @Nullable
    T getMod(PlatformModFile platformModFile);

    @Nullable
    T getMod(PlatformMod platformMod);

    List<T> getMods(ModPlatform platform, List<Object> projectIds);

    @Nullable
    T getModByCoordinates(String coords);

    List<T> getModsByName(String name);

    Multimap<String, T> getModsByNameAtLeast2();

    @Nullable
    T getModByFileHash(String fileSha1);

    T createMod(ModFileInfo modInfo);

    @Nullable
    T getLoaderMod(String coords);

    T createLoaderMod(ModFileInfo modInfo);

    @Nullable
    Instant getKnownLatestProjectFileDate(PlatformModFile file);

    void markKnownById(PlatformModFile file, Instant latestProjectFileDate);

    <E extends Exception> void trackMod(T mod, ThrowingConsumer<ModTracker, E> consumer) throws E;

    interface ModTracker {
        void insertClasses(List<ClassData> classes);

        void insertTags(List<TagFile> tags);

        void insertEnumExtensions(List<EnumExtension> extensions);

        void deleteCurrent();

        void markAsKnown(String fileSha1);

        void setIndexDate(Instant date);
    }

    interface DatabaseMod<T extends DatabaseMod<T>> {
        String getVersion();

        String getName();

        @Nullable
        String getMavenCoordinate();

        @Nullable
        Integer getCurseForgeProjectId();

        @Nullable
        String getModrinthProjectId();

        Map<String, Object> getPlatformIds();

        @Nullable
        default Object getProjectId(ModPlatform platform) {
            return platform == Main.MODRINTH_PLATFORM ? getModrinthProjectId() : getCurseForgeProjectId();
        }

        boolean isLoader();

        void updateMetadata(ModFileInfo info);

        void link(PlatformModFile platformFile);

        void link(String mavenCoords);

        void transferTo(T other);

        void delete();
    }
}
