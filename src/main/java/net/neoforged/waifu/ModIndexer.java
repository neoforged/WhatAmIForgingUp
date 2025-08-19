package net.neoforged.waifu;

import io.github.matyrobbrt.curseforgeapi.util.Pair;
import net.neoforged.waifu.db.ClassData;
import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.index.data.DataIndexer;
import net.neoforged.waifu.index.data.DataMapCollector;
import net.neoforged.waifu.index.EnumExtensionCollector;
import net.neoforged.waifu.index.FileTreeWalker;
import net.neoforged.waifu.index.IndexingClassVisitor;
import net.neoforged.waifu.index.ModFileIndexer;
import net.neoforged.waifu.index.Remapper;
import net.neoforged.waifu.index.data.RecipeCollector;
import net.neoforged.waifu.index.data.TagCollector;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFilePath;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformProject;
import net.neoforged.waifu.platform.PlatformProjectFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.ProgressMonitor;
import net.neoforged.waifu.util.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipException;

public class ModIndexer<T extends IndexDatabase.DatabaseMod<T>> {
    private static final boolean KEEP_CACHES = Boolean.parseBoolean(System.getenv().getOrDefault("KEEP_PLATFORM_CACHES", "true"));
    public static final List<ModFileIndexer> DEFAULT_INDEXERS = List.of(
            new EnumExtensionCollector(), DataIndexer.withFallback(
                    TagCollector::new,
                    DataMapCollector::new,
                    RecipeCollector::new
            )
    );

    private final Path baseCacheFolder;
    private final IndexDatabase<T> db;
    private final String gameVersion;
    private final ModLoader loader;

    private final Remapper remapper;

    private final List<IndexCandidate> candidateMods = new ArrayList<>();

    private final List<ModFileIndexer> indexers;

    public ModIndexer(Path baseCacheFolder, IndexDatabase<T> db, String gameVersion, ModLoader loader) {
        this(baseCacheFolder, db, gameVersion, loader, Remapper.NOOP, DEFAULT_INDEXERS);
    }

    public ModIndexer(Path baseCacheFolder, IndexDatabase<T> db, String gameVersion, ModLoader loader, Remapper remapper, List<ModFileIndexer> indexers) {
        this.baseCacheFolder = baseCacheFolder;
        this.db = db;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.remapper = remapper;
        this.indexers = indexers;
    }

    public void indexLoaderMod(ModFileInfo info) throws IOException {
        var mod = db.getLoaderMod(info.getMavenCoordinates());
        if (mod == null) {
            mod = db.createLoaderMod(info);
        }

        indexAndPrepareUpload(null, info, mod, false, DataSanitizer.of(
                DataSanitizer.REMOVE_ANONYMOUS_CLASSES, DataSanitizer.REMOVE_LAMBDAS
        )).run();
    }

    public List<IndexCandidate> index(ModPlatform platform, ExecutorService executor, int concurrency, ProgressMonitor<IndexCandidate> monitor, DataSanitizer sanitizer) {
        var expansionResult = getExpandedMods(platform);
        if (expansionResult.candidates.isEmpty()) return List.of();

        var expanded = expansionResult.candidates();
        monitor.setExpected(expanded);

        var cfs = new ArrayList<CompletableFuture<Pair<Runnable, IndexCandidate>>>(concurrency);

        for (IndexCandidate fromPlatform : expanded) {
            var cf = new CompletableFuture<Pair<Runnable, IndexCandidate>>();

            executor.submit(() -> {
                try {
                    var runnable = run(fromPlatform, sanitizer, monitor);
                    if (runnable == null) {
                        monitor.unexpect(fromPlatform);
                    } else {
                        monitor.markAsIndexed(fromPlatform);
                    }
                    cf.complete(Pair.of(runnable, fromPlatform));
                } catch (Throwable t) {
                    monitor.raiseError(fromPlatform, t);
                    cf.complete(null); // We can't completeExceptionally to avoid join throwing
                }

                return null;
            });

            cfs.add(cf);

            if (cfs.size() == concurrency) {
                runCurrent(monitor, cfs);
            }
        }

        if (!cfs.isEmpty()) {
            runCurrent(monitor, cfs);
        }

        for (var entry : expansionResult.additionalMavenCoordinates.entrySet()) {
            var mod = db.getMod(entry.getKey());
            if (mod != null && mod.getMavenCoordinate() == null) {
                mod.link(entry.getValue());
            }
        }

        return expanded;
    }

    private void runCurrent(ProgressMonitor<IndexCandidate> monitor, List<CompletableFuture<Pair<Runnable, IndexCandidate>>> cfs) {
        var runs = Utils.allOf(cfs).join();
        for (var run : runs) {
            if (run != null && run.first() != null) {
                try {
                    run.first().run();
                    monitor.markAsStored(run.second());
                } catch (Throwable ex) {
                    monitor.raiseError(run.second(), ex);
                }
            }
        }

        cfs.clear();
    }

    public static <T extends IndexDatabase.DatabaseMod<T>> void merge(IndexDatabase<T> db, T linkTo, PlatformProjectFile platformFile) {
        var platformMod = db.getMod(platformFile);

        // If we previously linked this file to a project and now we know this file is also part of a different project on a different platform
        // keep the new project and delete the old one to make sure we do not duplicate mods
        if (platformMod != null && !Objects.equals(platformMod, linkTo)) {
            platformMod.transferTo(linkTo);
            platformMod.delete();
        }

        linkTo.link(platformFile);
    }

    private @Nullable Runnable run(IndexCandidate file, DataSanitizer sanitizer, ProgressMonitor<IndexCandidate> monitor) throws IOException {
        var knownByHash = db.getModByFileHash(file.file.getFileHash());
        if (knownByHash != null) {
            // This file was indexed already so we'll skip up, but we'll just make sure that it's linked
            if (file.platformFile != null) {
                merge(db, knownByHash, file.platformFile);
                db.markKnownById(file.platformFile, Objects.requireNonNullElse(file.platformFile.getMod().getLatestReleaseDate(), Instant.EPOCH));
            } else if (file.file().getMavenCoordinates() != null && knownByHash.getMavenCoordinate() == null) {
                knownByHash.link(file.file().getMavenCoordinates());
            }
            return null;
        }

        T mod = null;
        if (file.platformFile != null) {
            mod = db.getMod(file.platformFile);
            if (mod == null) {
                // Now we're going to try some more aggressive checks to see if we can merge this mod with an existing one just this time.
                var sameName = db.getModsByName(file.file().getDisplayName());
                if (!sameName.isEmpty()) {
                    var isCurseForge = file.platformFile.getPlatform() == Main.CURSE_FORGE_PLATFORM;

                    var ownHashes = StreamSupport.stream(Spliterators.spliteratorUnknownSize(file.platformFile.getMod().getFilesForVersion(gameVersion, loader), Spliterator.ORDERED), false)
                            .map(PlatformProjectFile::getHash)
                            .collect(Collectors.toSet());

                    for (T candidateMod : sameName) {
                        PlatformProject otherMod = null;
                        if (candidateMod.getCurseForgeProjectId() == null && isCurseForge && candidateMod.getModrinthProjectId() != null) {
                            otherMod = Main.MODRINTH_PLATFORM.getProjectById(candidateMod.getModrinthProjectId());
                        } else if (candidateMod.getModrinthProjectId() == null && !isCurseForge && candidateMod.getCurseForgeProjectId() != null) {
                            otherMod = Main.CURSE_FORGE_PLATFORM.getProjectById(candidateMod.getCurseForgeProjectId());
                        }

                        if (otherMod == null) continue;

                        var otherFile = otherMod.getLatestFile(gameVersion, loader);
                        if (otherFile != null) {
                            try (var otherIn = Files.newInputStream(download(otherFile));
                                 var thisIn = file.file().openStream()) {
                                // If we find that this mod matches the other's mod jar (when we strip dates in zip entries or in the manifest) we're confident to link them
                                if (Arrays.equals(
                                        Utils.createCleanZip(otherIn),
                                        Utils.createCleanZip(thisIn)
                                )) {
                                    mod = candidateMod;
                                    mod.link(file.platformFile);
                                    break;
                                }
                            }
                        }

                        var candidateHashes = StreamSupport.stream(Spliterators.spliteratorUnknownSize(otherMod.getFilesForVersion(gameVersion, loader), Spliterator.ORDERED), false)
                                .map(PlatformProjectFile::getHash)
                                .collect(Collectors.toSet());

                        // If the two mods have at least one file in common for the same game version merge them
                        var common = CollectionUtils.intersection(ownHashes, candidateHashes);
                        if (!common.isEmpty()) {
                            mod = candidateMod;
                            mod.link(file.platformFile);
                            break;
                        }
                    }
                }

                // TODO - other heuristics too???? pain pain pain

                if (mod == null) {
                    mod = db.createMod(file.file());
                    mod.link(file.platformFile);
                    monitor.markAsNew(file);
                }
            }
        } else if (file.file.getMavenCoordinates() != null) {
            mod = db.getModByCoordinates(file.file.getMavenCoordinates());
            if (mod == null) {
                mod = db.createMod(file.file);
                monitor.markAsNew(file);
            } else if (new DefaultArtifactVersion(mod.getVersion()).compareTo(file.file().getVersion()) >= 0) {
                // Do not bother indexing an older version
                return null;
            }
        }

        if (mod == null) {
            // We have nothing to base mod identification on... so we quit
            return null;
        }

        return indexAndPrepareUpload(file.platformFile(), file.file(), mod, true, sanitizer);
    }

    private Runnable indexAndPrepareUpload(@Nullable PlatformProjectFile platform, ModFileInfo file, T mod, boolean refs, DataSanitizer sanitizer) throws IOException {
        var walker = FileTreeWalker.from(file.getRootDirectory());

        List<ClassData> classes = IndexingClassVisitor.collect(file.getRootDirectory(), refs, refs, remapper); // TODO - do we want a separate parameter?

        List<Consumer<IndexDatabase.ModTracker>> queue = new ArrayList<>(indexers.size());
        for (ModFileIndexer indexer : indexers) {
            var task = indexer.collectAndPrepareUpsert(file, walker);
            if (task != null) {
                queue.add(task);
            }
        }

        var sanitized = sanitizer.sanitize(classes);

        return () -> {
            db.trackMod(mod, tracker -> {
                tracker.deleteCurrent();

                tracker.insertClasses(sanitized);

                for (var consumer : queue) {
                    consumer.accept(tracker);
                }

                tracker.setIndexDate(Instant.now());

                if (!mod.isLoader()) {
                    tracker.markAsKnown(file.getFileHash());
                }
            });

            mod.updateMetadata(file);

            if (platform != null) {
                db.markKnownById(platform, Objects.requireNonNullElse(platform.getMod().getLatestReleaseDate(), Instant.EPOCH));
            }
        };
    }

    public record ExpansionResult(List<IndexCandidate> candidates, Map<PlatformProjectFile, String> additionalMavenCoordinates) {}

    public ExpansionResult getExpandedMods(ModPlatform platform) {
        Map<String, ModFileInfo.NestedJar> contained = new LinkedHashMap<>();

        Map<Object, PlatformProjectFile> projectsBeingIndexed = new HashMap<>();

        for (IndexCandidate platformMod : candidateMods) {
            addNestedMods(contained, platformMod.file);

            if (platformMod.platformFile() != null) {
                projectsBeingIndexed.put(platformMod.platformFile().getProjectId(), platformMod.platformFile());
            }
        }

        var candidateHashes = candidateMods.stream().collect(Collectors.toMap(k -> k.file().getFileHash(), Function.identity()));

        var additionalCoordinates = new HashMap<PlatformProjectFile, String>();

        contained.entrySet().removeIf(e -> {
            var linked = candidateHashes.get(e.getValue().info().getFileHash());
            if (linked != null) {
                additionalCoordinates.put(linked.platformFile(), e.getValue().identifier());
                return true;
            }
            return false;
        });

        var finalList = new ArrayList<IndexCandidate>(contained.size() + candidateMods.size());
        finalList.addAll(candidateMods);

        var jijFiles = contained.values().stream()
                .map(ModFileInfo.NestedJar::info)
                .toList();

        // We attempt to link JiJ'd mods to real projects on the platform based on their fingerprints
        var filesByFingerprint = platform.getFilesByFingerprint(jijFiles);
        for (int i = 0; i < jijFiles.size(); i++) {
            var fingerprinted = filesByFingerprint.get(i);
            if (fingerprinted != null) {
                var file = jijFiles.get(i);

                contained.remove(file.getMavenCoordinates());

                var alreadyIndexed = projectsBeingIndexed.get(fingerprinted.getProjectId());

                if (alreadyIndexed == null) {
                    additionalCoordinates.put(fingerprinted, file.getMavenCoordinates());
                    projectsBeingIndexed.put(fingerprinted.getProjectId(), fingerprinted);
                    finalList.add(new IndexCandidate(fingerprinted, file));
                } else {
                    additionalCoordinates.put(alreadyIndexed, file.getMavenCoordinates());
                }
            }
        }

        for (ModFileInfo.NestedJar value : contained.values()) {
            finalList.add(new IndexCandidate(null, value.info()));
        }

        return new ExpansionResult(finalList, additionalCoordinates);
    }

    private void addNestedMods(Map<String, ModFileInfo.NestedJar> nested, ModFileInfo mod) {
        for (ModFileInfo.NestedJar nestedJar : mod.getNestedJars()) {
            nested.merge(
                    nestedJar.identifier(),
                    nestedJar,
                    (oldJar, newJar) -> {
                        if (oldJar.version().compareTo(newJar.version()) < 0) {
                            return newJar;
                        }
                        return oldJar;
                    }
            );
            addNestedMods(nested, nestedJar.info());
        }
    }

    public synchronized void downloadAndConsiderConcurrently(List<PlatformProjectFile> files, ExecutorService executor, Counter<PlatformProjectFile> downloadCounter) {
        Main.LOGGER.info("Downloading {} files concurrently", files.size());
        var cfs = new ArrayList<CompletableFuture<IndexCandidate>>();
        for (PlatformProjectFile file : files) {
            var cf = new CompletableFuture<IndexCandidate>();
            executor.submit(() -> {
                try {
                    var path = download(file);

                    try {
                        var mod = loader.getReader().read(
                                new ModFilePath(
                                        path, FileSystems.newFileSystem(path).getRootDirectories().iterator().next(),
                                        file.getHash(), KEEP_CACHES ? null : path
                                ),
                                null, null
                        );

                        downloadCounter.add(file);

                        if (mod != null) {
                            cf.complete(new IndexCandidate(file, mod));
                        } else {
                            cf.complete(null);
                        }
                    } catch (ZipException zip) {
                        Main.LOGGER.error("Failed to read mod file {} at path {} due to zip exception. File is probably corrupted.", file, path, zip);
                        cf.complete(null);
                    }
                } catch (Throwable ex) {
                    cf.completeExceptionally(ex);
                }

                return null;
            });
            cfs.add(cf);
        }

        for (IndexCandidate indexCandidate : Utils.allOf(cfs).join()) {
            if (indexCandidate != null) {
                this.candidateMods.add(indexCandidate);
            }
        }

        Main.LOGGER.info("Finished downloading files");
    }

    private Path download(PlatformProjectFile file) throws IOException {
        var path = baseCacheFolder.resolve(file.getPlatform().getName()).resolve(file.getId() + ".jar");

        try {
            var diskLen = Files.size(path);
            if (diskLen == file.getFileLength()) {
                return path;
            }
        } catch (IOException ignored) {}

        Files.createDirectories(path.getParent());
        try (var in = file.download();
             var os = Files.newOutputStream(path)) {
            in.transferTo(os);
        }

        return path;
    }

    public record IndexCandidate(@Nullable PlatformProjectFile platformFile, ModFileInfo file) {}
}
