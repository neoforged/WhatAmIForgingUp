package net.neoforged.waifu.db;

import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.ThrowingConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IndexDatabase<T extends IndexDatabase.DatabaseMod> extends AutoCloseable {
    @Nullable
    T getMod(PlatformModFile platformModFile);

    @Nullable
    T getMod(String coords);

    @Nullable
    T getModByFileHash(String fileSha1);

    T createMod(ModFileInfo modInfo);

    @Nullable
    T getLoaderMod(String coords);

    T createLoaderMod(ModFileInfo modInfo);

    boolean isKnown(PlatformModFile file);

    void markKnownById(PlatformModFile file);

    <E extends Exception> void trackMod(T mod, ThrowingConsumer<ModTracker, E> consumer) throws E;

    interface ModTracker {
        void insertClasses(List<ClassData> classes);

        void insertTags(List<TagFile> tags);

        void deleteCurrent();

        void markAsKnown(String fileSha1);
    }

    interface DatabaseMod {
        String getVersion();

        boolean isLoader();

        void updateMetadata(ModFileInfo info);

        void link(PlatformModFile platformFile);

        void delete();
    }
}
