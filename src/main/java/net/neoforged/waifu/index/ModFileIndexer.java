package net.neoforged.waifu.index;

import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.meta.ModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Consumer;

public interface ModFileIndexer {
    @Nullable
    Consumer<IndexDatabase.ModTracker> collectAndPrepareUpsert(
            ModFileInfo modFile, FileTreeWalker walker
    ) throws IOException;
}
