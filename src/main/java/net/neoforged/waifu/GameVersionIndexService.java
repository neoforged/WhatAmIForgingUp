package net.neoforged.waifu;

import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.index.Remapper;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformMod;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.ProgressMonitor;
import net.neoforged.waifu.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class GameVersionIndexService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameVersionIndexService.class);
    public static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name("indexing-service-", 0).uncaughtExceptionHandler(Utils.LOG_EXCEPTIONS).factory());
    public static final int CONCURRENCY = 100;

    private volatile boolean isRunning, pendingReRun;

    private final String version;
    private final ModLoader loader;

    private final List<ModPlatform> platforms;

    private final IndexDatabase<?> db;
    private final DataSanitizer sanitizer;
    private final ListenerFactory listenerFactory;

    private final Path platformCache;

    public GameVersionIndexService(String version, ModLoader loader, List<ModPlatform> platforms, IndexDatabase<?> db, DataSanitizer sanitizer, ListenerFactory listenerFactory) {
        this.version = version;
        this.loader = loader;
        this.platforms = platforms;
        this.db = db;
        this.sanitizer = sanitizer;
        this.listenerFactory = listenerFactory;

        this.platformCache = Main.PLATFORM_CACHE;
    }

    @Override
    public void run() {
        if (isRunning) {
            pendingReRun = true;
            return;
        }

        isRunning = true;

        LOGGER.info("Starting index for game version {} and loader {}", version, loader);

        runWithExceptions();

        isRunning = false;

        if (pendingReRun) {
            pendingReRun = false;
            run();
        }
    }

    public void runWithExceptions() {
        Remapper remapper = Remapper.NOOP;
        try {
            remapper = loader.createRemapper(version);
        } catch (Exception exception) {
            Main.LOGGER.error("Failed to create remapper for game version {}, loader {}: ", version, loader, exception);
            listenerFactory.informError("Failed to create remapper for game version " + version + ", loader " + loader + ": " + exception.getMessage());
        }

        for (ModPlatform platform : platforms) {
            var listener = listenerFactory.startIndexingListener(version, loader, platform);

            try {
                var indexer = new ModIndexer<>(platformCache, db, version, loader, remapper);

                LOGGER.info("Scanning platform {} for game version {} and loader {}", platform.getName(), version, loader);
                var counter = listener.startPlatformScan();

                var modIds = new HashSet<>();

                var files = new ArrayList<PlatformModFile>();
                var itr = platform.searchMods(version, loader, ModPlatform.SearchSortField.LAST_UPDATED);
                while (itr.hasNext()) {
                    var next = itr.next();
                    if (!next.isAvailable()) continue;
                    var file = next.getLatestFile(version, loader);
                    if (file == null) continue;

                    var latestKnown = db.getKnownLatestProjectFileDate(file);
                    if (latestKnown != null) {
                        // We only stop if we found a known file that is also latest for that project since the project could be latest updated but for a different version
                        if (next.getLatestReleaseDate() != null && next.getLatestReleaseDate().getEpochSecond() == latestKnown.getEpochSecond()) { // Modrinth returns the date with big precision
                            break;
                        } else {
                            continue;
                        }
                    }

                    // A mod might be updated just as we're paginating the list, which would shift all entries to the right and cause some entries to be duplicated
                    if (modIds.add(file.getModId())) {
                        files.add(file);
                        counter.add(file);
                    }
                }

                // To make sure that we index all mods we get a page of the mods sorted by newest
                // Note - this is based on project IDs rather than file IDs to avoid needing to make 100 more queries (in the case of Modrinth) to
                // get the latest file for each project - after all if the file is new it would have already been indexed by the normal search anyway
                var latestReleasedMod = new ArrayList<PlatformMod>();
                int latestAmount = 0;
                var latestItr = platform.searchMods(version, loader, ModPlatform.SearchSortField.NEWEST_RELEASED);
                while (latestItr.hasNext() && latestAmount < platform.pageLimit()) {
                    var next = latestItr.next();
                    latestAmount++;
                    if (!next.isAvailable()) continue;
                    if (modIds.add(next.getId())) {
                        latestReleasedMod.add(next);
                    }
                }

                // If we found at least one newest released mod that isn't about to be indexed
                if (!latestReleasedMod.isEmpty()) {
                    // ...try to get the mods of those newest released which we have NOT indexed yet
                    var knownModIds = db.getMods(platform, latestReleasedMod.stream().map(PlatformMod::getId).toList())
                            .stream().map(m -> m.getProjectId(platform))
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    for (PlatformMod mod : latestReleasedMod) {
                        if (!knownModIds.contains(mod.getId())) {
                            // ...and queue them for indexing
                            var file = mod.getLatestFile(version, loader);
                            if (file != null) {
                                files.add(0, file);
                            }
                        }
                    }
                }

                platform.bulkFillData(files);

                // Reverse the order of the files so we index older ones first
                Collections.reverse(files);

                var downloadCounter = listener.startDownload();

                try (var exec = Executors.newFixedThreadPool(10, Thread.ofVirtual().name("mod-downloader-" + platform.getName() + "-" + version + "-", 0)
                        .uncaughtExceptionHandler(Utils.LOG_EXCEPTIONS).factory())) {
                    indexer.downloadAndConsiderConcurrently(files, exec, downloadCounter);
                }

                var monitor = listener.startIndex();
                var scanned = indexer.index(platform, VIRTUAL_THREAD_EXECUTOR, CONCURRENCY, monitor, sanitizer);

                for (ModIndexer.IndexCandidate indexCandidate : scanned) {
                    // Only manually close platform files as they'll propagate the close call to their nested jars in the correct order
                    if (indexCandidate.platformFile() != null) {
                        try {
                            indexCandidate.file().close();
                        } catch (NoSuchFileException ignored) {
                            // We ignore this exception since sometimes mods JiJing files that end up with the same file cache
                            // will be "closed" twice
                        }
                    }
                }

                listener.markFinish(scanned.size());

                LOGGER.info("Finished indexing platform {} for game version {} and loader {}", platform.getName(), version, loader);
            } catch (Exception exception) {
                LOGGER.error("Fatal error raised while indexing platform {} for game version {} and loader {}", platform.getName(), version, loader, exception);
                listener.raiseFatalException(exception);
            }
        }

        var loaderProvider = loader.getVersionProvider();
        if (loaderProvider != null) {
            var loaderVersion = loaderProvider.latestVersion().apply(version);
            var loaderDbMod = db.getLoaderMod(loaderProvider.artifactName());

            if (loaderDbMod == null || !loaderDbMod.getVersion().equals(loaderVersion)) {
                LOGGER.info("Indexing loader {} for game version {}. Found new version: {}", loader, version, loaderVersion);
                try {
                    var indexer = new ModIndexer<>(platformCache, db, version, loader);
                    var loaderMods = loaderProvider.jarProvider().apply(loaderVersion);
                    for (ModFileInfo loaderMod : loaderMods) {
                        indexer.indexLoaderMod(loaderMod);
                        loaderMod.close();
                    }
                    LOGGER.info("Indexed loader {} for game version {}", loader, version);
                } catch (Exception exception) {
                    LOGGER.error("Failed indexing loader {} version {} for game version {}", loader, loaderVersion, version, exception);
                    listenerFactory.informError("Failed to index loader version `" + loaderVersion + "`: " + exception.getMessage() + "\nCheck the log for more details.");
                }
            }
        }
    }

    public interface ListenerFactory {
        Listener startIndexingListener(String gameVersion, ModLoader loader, ModPlatform platform);

        void informError(String error);
    }

    public interface Listener {
        ProgressMonitor<ModIndexer.IndexCandidate> startIndex();

        Counter<PlatformModFile> startPlatformScan();

        Counter<PlatformModFile> startDownload();

        void markFinish(int scanned);

        void raiseFatalException(Exception exception);
    }
}
