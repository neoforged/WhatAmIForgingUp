package net.neoforged.waifu;

import net.neoforged.waifu.db.ClassData;
import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.index.IndexingClassVisitor;
import net.neoforged.waifu.index.TagCollector;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFilePath;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.ProgressMonitor;
import net.neoforged.waifu.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ModIndexer<T extends IndexDatabase.DatabaseMod> {
    private static final boolean KEEP_CACHES = Boolean.parseBoolean(System.getenv().getOrDefault("KEEP_PLATFORM_CACHES", "true")));
    private final Path baseCacheFolder;
    private final IndexDatabase<T> db;

    private final List<IndexCandidate> candidateMods = new ArrayList<>();

    public ModIndexer(Path baseCacheFolder, IndexDatabase<T> db) {
        this.baseCacheFolder = baseCacheFolder;
        this.db = db;
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

    public List<IndexCandidate> index(ExecutorService executor, int concurrency, ProgressMonitor<IndexCandidate> monitor, DataSanitizer sanitizer) {
        var expanded = getExpandedMods();

        monitor.setExpected(expanded);

        var cfs = new ArrayList<CompletableFuture<Pair<Runnable, IndexCandidate>>>(concurrency);

        for (IndexCandidate fromPlatform : expanded) {
            var cf = new CompletableFuture<Pair<Runnable, IndexCandidate>>();
            cfs.add(cf);

            executor.submit(() -> {
                try {
                    var runnable = run(fromPlatform, sanitizer);
                    cf.complete(Pair.of(runnable, fromPlatform));
                    monitor.markAsIndexed(fromPlatform);
                } catch (Throwable t) {
                    cf.completeExceptionally(t);
                    monitor.raiseError(fromPlatform, t);
                    throw t;
                } finally {
                    cf.complete(null);
                }

                return null;
            });

            if (cfs.size() == concurrency) {
                runCurrent(monitor, cfs);
            }
        }

        if (!cfs.isEmpty()) {
            runCurrent(monitor, cfs);
        }

        return expanded;
    }

    private void runCurrent(ProgressMonitor<IndexCandidate> monitor, List<CompletableFuture<Pair<Runnable, IndexCandidate>>> cfs) {
        Utils.allOf(cfs)
                .thenAccept(runs -> {
                    for (var run : runs) {
                        if (run.getKey() != null) {
                            try {
                                run.getKey().run();
                                monitor.markAsStored(run.getRight());
                            } catch (Throwable ex) {
                                monitor.raiseError(run.getRight(), ex);
                            }
                        }
                    }
                })
                .join();

        cfs.clear();
    }

    private @Nullable Runnable run(IndexCandidate file, DataSanitizer sanitizer) throws IOException {
        var knownByHash = db.getModByFileHash(file.file.getFileHash());
        if (knownByHash != null) {
            // This file was indexed already so we'll skip up, but we'll just make sure that it's linked
            if (file.platformFile != null) {
                var platformMod = db.getMod(file.platformFile);

                // If we previously linked this file to a project and now we know this file is also part of a different project on a different platform
                // keep the new project and delete the old one to make sure we do not duplicate mods
                if (platformMod != null && !Objects.equals(platformMod, knownByHash)) {
                    platformMod.delete();
                }

                knownByHash.link(file.platformFile);
                db.markKnownById(file.platformFile);
            }
            return null;
        }

        T mod = null;
        if (file.platformFile != null) {
            // TODO - heuristic to determine if files from different platforms are for the same project (besides file hashes)
            mod = db.getMod(file.platformFile);
            if (mod == null) {
                mod = db.createMod(file.file());
                mod.link(file.platformFile);
            }
        } else if (file.file.getMavenCoordinates() != null) {
            mod = db.getMod(file.file.getMavenCoordinates());
            if (mod == null) {
                mod = db.createMod(file.file);
            }
        }

        if (mod == null) {
            // We have nothing to base mod identification on... so we quit
            return null;
        }

        return indexAndPrepareUpload(file.platformFile(), file.file(), mod, true, sanitizer);
    }

    private Runnable indexAndPrepareUpload(@Nullable PlatformModFile platform, ModFileInfo file, T mod, boolean refs, DataSanitizer sanitizer) throws IOException {
        List<ClassData> classes = IndexingClassVisitor.collect(file.getRootDirectory(), refs);

        var tags = TagCollector.collect(file.getPath("data"));

        var sanitized = sanitizer.sanitize(classes);

        return () -> {
            db.trackMod(mod, tracker -> {
                tracker.deleteCurrent();

                tracker.insertClasses(sanitized);
                tracker.insertTags(tags);

                if (!mod.isLoader()) {
                    tracker.markAsKnown(file.getFileHash());
                }
            });

            mod.updateMetadata(file);

            if (platform != null) {
                db.markKnownById(platform);
            }
        };
    }

    public List<IndexCandidate> getExpandedMods() {
        Map<String, ModFileInfo.NestedJar> contained = new LinkedHashMap<>();
        Map<String, IndexCandidate> byModId = LinkedHashMap.newLinkedHashMap(candidateMods.size());

        for (IndexCandidate platformMod : candidateMods) {
            addNestedMods(contained, platformMod.file);

            var mods = platformMod.file.getMods();
            if (!mods.isEmpty()) {
                byModId.put(mods.get(0).modId(), platformMod);
            }
        }

        contained.entrySet().removeIf((entry) -> {
            var nested = entry.getValue().info();
            if (!nested.getMods().isEmpty()) {
                var modId = nested.getMods().get(0).modId();
                byModId.merge(modId, new IndexCandidate(null, nested), (oldJar, newJar) -> {
                    if (oldJar.file().getVersion().compareTo(newJar.file().getVersion()) < 0) {
                        return newJar.platformFile() == null ? new IndexCandidate(oldJar.platformFile, newJar.file) : newJar;
                    }
                    return oldJar;
                });
                return true;
            }
            return false;
        });

        var finalList = new ArrayList<IndexCandidate>(contained.size() + byModId.size());
        finalList.addAll(byModId.values());
        for (ModFileInfo.NestedJar value : contained.values()) {
            finalList.add(new IndexCandidate(null, value.info()));
        }

        return finalList;
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

    public synchronized void addFile(PlatformModFile file) throws IOException {
        var path = download(file);

        var mod = ModFileInfo.read(
                new ModFilePath(
                        FileSystems.newFileSystem(path).getRootDirectories().iterator().next(),
                        file.getHash(), KEEP_CACHES ? null : path
                ),
                null, null
        );

        if (mod != null) {
            this.candidateMods.add(new IndexCandidate(file, mod));
        }
    }

    private Path download(PlatformModFile file) throws IOException {
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

    public record IndexCandidate(@Nullable PlatformModFile platformFile, ModFileInfo file) {}
}
