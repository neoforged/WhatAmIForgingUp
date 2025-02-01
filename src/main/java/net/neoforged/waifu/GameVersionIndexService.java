package net.neoforged.waifu;

import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.NeoForgeJarProvider;
import net.neoforged.waifu.util.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameVersionIndexService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameVersionIndexService.class);
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean isRunning, pendingReRun;

    private final String version;
    private final List<ModPlatform> platforms;
    private final IndexDatabase<?> db;
    private final DataSanitizer sanitizer;
    private final ListenerFactory listenerFactory;

    private final Path platformCache;

    public GameVersionIndexService(String version, List<ModPlatform> platforms, IndexDatabase<?> db, DataSanitizer sanitizer, ListenerFactory listenerFactory) {
        this.version = version;
        this.platforms = platforms;
        this.db = db;
        this.sanitizer = sanitizer;
        this.listenerFactory = listenerFactory;

        this.platformCache = Main.CACHE.resolve("platform");
    }

    @Override
    public void run() {
        if (isRunning) {
            pendingReRun = true;
            return;
        }

        isRunning = true;

        LOGGER.info("Starting index for game version {}", version);

        runWithExceptions();

        isRunning = false;

        if (pendingReRun) {
            pendingReRun = false;
            run();
        }
    }

    public void runWithExceptions() {
        for (ModPlatform platform : platforms) {
            var listener = listenerFactory.startIndexingListener(version, platform);

            try {
                var indexer = new ModIndexer<>(platformCache, db);

                LOGGER.info("Scanning platform {} for game version {}", platform.getName(), version);
                var counter = listener.startPlatformScan(platform);

                var files = new ArrayList<PlatformModFile>();
                var itr = platform.searchMods(version);
                while (itr.hasNext()) {
                    var next = itr.next();
                    var file = next.getLatestFile(version);
                    if (file == null) continue;

                    var latestKnown = db.getKnownLatestProjectFileDate(file);
                    if (latestKnown != null) {
                        // We only stop if we found a known file that is also latest for that project since the project could be latest updated but for a different version
                        if (next.getLatestReleaseDate().getEpochSecond() == latestKnown.getEpochSecond()) { // Modrinth returns the date with big precision
                            break;
                        } else {
                            continue;
                        }
                    }
                    files.add(file);
                    counter.add(file);
                }

                // Reverse the order of the files so we index older ones first
                Collections.reverse(files);

                try (var exec = Executors.newFixedThreadPool(20, Thread.ofVirtual().name("mod-downloader-" + platform.getName() + "-" + version + "-", 0).factory())) {
                    indexer.downloadAndConsiderConcurrently(files, exec);
                }

                var monitor = listener.startIndex();
                var scanned = indexer.index(platform, VIRTUAL_THREAD_EXECUTOR, 100, monitor, sanitizer);

                for (ModIndexer.IndexCandidate indexCandidate : scanned) {
                    indexCandidate.file().close();
                }

                listener.markFinish(scanned.size());

                LOGGER.info("Finished indexing platform {} for game version {}", platform.getName(), version);
            } catch (Exception exception) {
                LOGGER.error("Fatal error raised while indexing platform {} for game version {}", platform.getName(), version, exception);
                listener.raiseFatalException(exception);
            }
        }

        var loaderVersion = NeoForgeJarProvider.getLatestVersion(version);
        var mod = db.getLoaderMod("net.neoforged:neoforge");

        if (mod == null || !mod.getVersion().equals(loaderVersion)) {
            LOGGER.info("Indexing loader for game version {}. Found new version: {}", version, loaderVersion);
            try {
                var indexer = new ModIndexer<>(platformCache, db);
                var loaderMods = NeoForgeJarProvider.provide(loaderVersion);
                for (ModFileInfo loaderMod : loaderMods) {
                    indexer.indexLoaderMod(loaderMod);
                }
                LOGGER.info("Indexed loader for game version {}", version);
            } catch (Exception exception) {
                LOGGER.error("Failed indexing loader {} for game version {}", loaderVersion, version, exception);
                listenerFactory.informError("Failed to index loader version `" + loaderVersion + "`: " + exception.getMessage() + "\nCheck the log for more details.");
            }
        }
    }

    public interface ListenerFactory {
        Listener startIndexingListener(String gameVersion, ModPlatform platform);

        void informError(String error);
    }

    public interface Listener {
        ProgressMonitor<ModIndexer.IndexCandidate> startIndex();

        Counter<PlatformModFile> startPlatformScan(ModPlatform platform);

        void markFinish(int scanned);

        void raiseFatalException(Exception exception);
    }
}
