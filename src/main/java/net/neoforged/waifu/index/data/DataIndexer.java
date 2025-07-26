package net.neoforged.waifu.index.data;

import com.google.gson.JsonElement;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.db.JsonFile;
import net.neoforged.waifu.index.FileTreeWalker;
import net.neoforged.waifu.index.ModFileIndexer;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class DataIndexer implements ModFileIndexer {
    private static final Pattern DATA_FILE_PATTERN = Pattern.compile("(?<namespace>[^/]*)/(?<type>[^/]*)/(?<path>.*)\\.json");

    private final boolean fallback;
    private final List<Supplier<DataFileIndexer>> indexers;

    @SafeVarargs
    private DataIndexer(boolean fallback, Supplier<DataFileIndexer>... indexers) {
        this.fallback = fallback;
        this.indexers = List.of(indexers);
    }

    @SafeVarargs
    public static ModFileIndexer withFallback(Supplier<DataFileIndexer>... indexers) {
        return new DataIndexer(true, indexers);
    }

    @SafeVarargs
    public static ModFileIndexer just(Supplier<DataFileIndexer>... indexers) {
        return new DataIndexer(false, indexers);
    }

    @Override
    public @Nullable Consumer<IndexDatabase.ModTracker> collectAndPrepareUpsert(ModFileInfo modFile, FileTreeWalker walker) throws IOException {
        List<DataFileIndexer> indexerList = this.indexers.stream().map(Supplier::get).toList();

        Map<String, DataFileIndexer> indexers = new HashMap<>();
        for (var dataFileIndexer : indexerList) {
            for (var folderName : dataFileIndexer.getFolderNames()) {
                indexers.put(folderName, dataFileIndexer);
            }
        }

        var otherDataFiles = new ArrayList<JsonFile>();

        walker.relative("data").walkMatching(DATA_FILE_PATTERN, (file, namePattern) -> {
            var namespace = namePattern.group(1);
            var type = namePattern.group(2);
            var path = namePattern.group(3);

            var indexer = indexers.get(type);

            try {
                if (indexer != null) {
                    try (var is = Files.newBufferedReader(file)) {
                        var element = Utils.GSON.fromJson(is, JsonElement.class);
                        indexer.accept(namespace, path, element);
                    }
                } else if (fallback) { // This is an optimisation - if we do not need to collect unhandled files as generic json files (that's what "fallback" means) then we don't need to hold it in memory even if just for a bit
                    try (var is = Files.newBufferedReader(file)) {
                        otherDataFiles.add(new JsonFile(
                                namespace + "/" + type + "/" + path,
                                // We go through JSON to parse it leniently (i.e. with comments)
                                // as PostgreSQL adheres to the strict standard
                                Utils.GSON.fromJson(is, JsonElement.class)
                        ));
                    }
                }
            } catch (Exception ignored) {

            }
        });
        return modTracker -> {
            for (var indexer : indexerList) {
                indexer.commit(modTracker);
            }

            if (fallback && !otherDataFiles.isEmpty()) {
                modTracker.insertDataFiles(otherDataFiles);
            }
        };
    }

    public interface DataFileIndexer {
        List<String> getFolderNames();

        void accept(String namespace, String path, JsonElement file) throws IOException;

        void commit(IndexDatabase.ModTracker modTracker);
    }
}
