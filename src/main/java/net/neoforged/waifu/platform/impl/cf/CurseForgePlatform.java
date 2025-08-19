package net.neoforged.waifu.platform.impl.cf;

import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.annotation.Nullable;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.query.FileListQuery;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.HashAlgo;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileHash;
import io.github.matyrobbrt.curseforgeapi.schemas.fingerprint.FingerprintMatch;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformProject;
import net.neoforged.waifu.platform.PlatformProjectFile;
import net.neoforged.waifu.util.MappingIterator;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CurseForgePlatform implements ModPlatform {
    private final CurseForgeAPI api;

    public CurseForgePlatform(CurseForgeAPI api) {
        this.api = api;
    }

    @Override
    public String getName() {
        return CURSEFORGE;
    }

    @Override
    public String getLogoUrl() {
        return "https://static-beta.curseforge.com/images/cf_legacy.png";
    }

    @Override
    public PlatformProject getProjectById(Object id) {
        try {
            return createMod(api.getHelper().getMod((int) id).orElseThrow());
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    private int classId(ProjectType projectType) {
        // 6 is mods, 4471 is modpacks
        return projectType == ProjectType.MOD ? 6 : 4471;
    }

    @Override
    public PlatformProject getProjectBySlug(String slug, ProjectType projectType) {
        try {
            var response = api.getHelper().searchMods(ModSearchQuery.of(Constants.GameIDs.MINECRAFT)
                            .slug(slug)
                            .classId(classId(projectType)))
                    .orElseThrow();
            if (response.isEmpty()) return null;
            return createMod(response.getFirst());
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterator<PlatformProject> searchProjects(String version, ModLoader loader, ProjectType projectType, SearchSortField sortField, @Nullable String query) {
        try {
            Supplier<ModSearchQuery> baseQuery = () -> ModSearchQuery.of(Constants.GameIDs.MINECRAFT)
                            .gameVersion(version).classId(classId(projectType))
                            .sortField(switch (sortField) {
                                case LAST_UPDATED -> ModSearchQuery.SortField.LAST_UPDATED;
                                case NEWEST_RELEASED -> ModSearchQuery.SortField.RELEASED_DATE;
                                case POPULARITY -> ModSearchQuery.SortField.POPULARITY;
                            })
                            .searchFilter(query)
                            .modLoaderType(loader(loader));

            var itr = new MappingIterator<>(api.getHelper().paginated(q -> Requests.searchModsPaginated(baseQuery.get()
                            .sortOrder(ModSearchQuery.SortOrder.DESCENDENT)
                            .paginated(q)), Function.identity())
                    .orElseThrow(), this::createMod);
            return new Iterator<>() {
                final Set<Object> known = new HashSet<>();
                int knownAmount = 0;
                Iterator<PlatformProject> delegate;

                @Override
                public boolean hasNext() {
                    if (delegate != null) return delegate.hasNext();

                    if (knownAmount == 10_000) return true;
                    return itr.hasNext();
                }

                @Override
                public PlatformProject next() {
                    if (delegate != null) return delegate.next();

                    if (!itr.hasNext() && knownAmount == 10_000) {
                        try {
                            var oppositeDir = new MappingIterator<>(api.getHelper().paginated(q -> Requests.searchModsPaginated(baseQuery.get()
                                            .sortOrder(ModSearchQuery.SortOrder.ASCENDENT)
                                            .paginated(q)), Function.identity())
                                    .orElseThrow(), CurseForgePlatform.this::createMod);

                            var items = new ArrayList<PlatformProject>();
                            while (oppositeDir.hasNext()) {
                                var next = oppositeDir.next();
                                if (known.contains(next.getId())) {
                                    break;
                                } else {
                                    items.add(next);
                                }
                            }

                            Collections.reverse(items);
                            delegate = items.iterator();

                            return delegate.next();
                        } catch (CurseForgeException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    var nxt = itr.next();
                    known.add(nxt.getId());
                    knownAmount++;
                    return nxt;
                }
            };
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PlatformProjectFile> getFiles(List<Object> fileIds) {
        try {
            return api.getHelper().getFiles(fileIds.stream().mapToInt(i -> {
                        if (i instanceof Integer) {
                            return (int) i;
                        }
                        return Integer.parseInt(((String) i));
                    }).toArray())
                    .map(l -> l.stream().map(f -> createFile(null, f.id(), f)).toList())
                    .orElseThrow();
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PlatformProjectFile> getModsInPack(PlatformProjectFile pack) {
        record FilePointer(int projectID, int fileID) {}
        record Manifest(List<FilePointer> files) {}

        Manifest manifest = new Manifest(List.of());
        try (var is = new ZipInputStream(pack.download())) {
            ZipEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    try (final Reader reader = new InputStreamReader(is)) {
                        manifest = Utils.GSON.fromJson(reader, Manifest.class);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return getFiles(manifest.files().stream().map(FilePointer::fileID).<Object>map(Function.identity()).toList());
    }

    @Override
    public List<@org.jetbrains.annotations.Nullable PlatformProjectFile> getFilesByFingerprint(List<ModFileInfo> files) {
        try {
            var mods = new ArrayList<PlatformProjectFile>(files.size());
            for (int i = 0; i < files.size(); i++) mods.add(null);
            var murmurHashes = new ArrayList<Long>();
            for (ModFileInfo file : files) {
                murmurHashes.add(file.computeMurmur2());
            }
            var result = api.getHelper().getFingerprintMatches(murmurHashes.stream().mapToLong(value -> value).toArray())
                    .orElseThrow();

            for (FingerprintMatch exactMatch : result.exactMatches()) {
                for (int i = 0; i < murmurHashes.size(); i++) {
                    if (murmurHashes.get(i) == exactMatch.file().fileFingerprint()) {
                        mods.set(i, createFile(null, exactMatch.file().id(), exactMatch.file()));
                        break;
                    }
                }
            }

            return mods;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void bulkFillFiles(List<PlatformProjectFile> files) {
        var castFiles = (List<CFModFile>) (List) new ArrayList<>(files);
        castFiles.removeIf(f -> f.getCachedFile() != null);
        if (castFiles.isEmpty()) return;

        var byId = HashMap.<Integer, CFModFile>newHashMap(castFiles.size());
        for (CFModFile castFile : castFiles) {
            byId.put((int)castFile.getId(), castFile);
        }

        var partition = new ArrayList<CFModFile>(50);

        final Runnable request = () -> {
            try {
                var res = api.getHelper().getFiles(partition.stream().mapToInt(f -> (int) f.getId()).toArray())
                        .orElse(List.of());
                for (File file : res) {
                    byId.get(file.id()).setCachedFile(file);
                }
            } catch (Exception ex) {
                Utils.sneakyThrow(ex);
            }
        };

        for (CFModFile castFile : castFiles) {
            partition.add(castFile);
            if (partition.size() == 50) {
                request.run();
                partition.clear();
            }
        }

        if (!partition.isEmpty()) {
            request.run();
        }
    }

    @Override
    public int pageLimit() {
        return 50;
    }

    private PlatformProject createMod(Mod mod) {
        return new PlatformProject() {
            @Override
            public ModPlatform getPlatform() {
                return CurseForgePlatform.this;
            }

            @Override
            public Object getId() {
                return mod.id();
            }

            @Override
            public String getSlug() {
                return mod.slug();
            }

            @Override
            public String getTitle() {
                return mod.name();
            }

            @Override
            public String getDescription() {
                return mod.summary();
            }

            @Override
            public String getIconUrl() {
                return mod.logo().url();
            }

            @Override
            public long getDownloads() {
                return mod.downloadCount();
            }

            @Override
            public Instant getReleasedDate() {
                return mod.getDateReleasedAsInstant();
            }

            @Override
            public String getUrl() {
                return mod.links().websiteUrl();
            }

            @Override
            public PlatformProjectFile getLatestFile(String gameVersion, @Nullable ModLoader loader) {
                var ld = loader == null ? null : loader(loader);
                var idx = mod.latestFilesIndexes().stream()
                        .filter(f -> f.gameVersion().equals(gameVersion) && (ld == null || (f.modLoader() != null && f.modLoaderType() == ld)))
                        .limit(1)
                        .findFirst()
                        .orElse(null);
                return idx == null ? null : createFile(this, idx.fileId(), null);
            }

            @Override
            public Iterator<PlatformProjectFile> getAllFiles() {
                try {
                    return new MappingIterator<>(api.getHelper().listModFiles(mod)
                            .orElseThrow(), fl -> createFile(this, fl.id(), fl));
                } catch (Exception ex) {
                    Utils.sneakyThrow(ex);
                    throw null;
                }
            }

            @Override
            public Iterator<PlatformProjectFile> getFilesForVersion(String version, ModLoader loader) {
                try {
                    return new MappingIterator<>(api.getHelper().listModFiles(mod.id(), FileListQuery.of()
                                    .gameVersion(version).modLoaderType(loader(loader)))
                            .orElseThrow(), fl -> createFile(this, fl.id(), fl));
                } catch (Exception ex) {
                    Utils.sneakyThrow(ex);
                    throw null;
                }
            }

            @Override
            public Instant getLatestReleaseDate() {
                return mod.latestFiles()
                        .stream().map(f -> Instant.parse(f.fileDate()))
                        .max(Comparator.comparing(Function.identity()))
                        .orElse(null);
            }

            @Override
            public boolean isAvailable() {
                return mod.isAvailable();
            }
        };
    }

    private PlatformProjectFile createFile(@Nullable PlatformProject platformMod, int fileId, @Nullable File optionalFile) {
        return new CFModFile() {
            private File file = optionalFile;

            @Override
            public Object getProjectId() {
                return this.mod == null ? getFile().modId() : this.mod.getId();
            }

            @Override
            public Object getId() {
                return fileId;
            }

            private PlatformProject mod = platformMod;
            @Override
            public PlatformProject getMod() {
                if (mod == null) {
                    try {
                        mod = createMod(api.getHelper().getMod(getFile().modId()).orElseThrow());
                    } catch (CurseForgeException e) {
                        throw new RuntimeException(e);
                    }
                }
                return mod;
            }

            @Override
            public String getHash() {
                for (FileHash hash : getFile().hashes()) {
                    if (hash.algo() == HashAlgo.SHA1) {
                        return hash.value();
                    }
                }
                throw new IllegalArgumentException("No hash?");
            }

            @Override
            public long getFileLength() {
                return getFile().fileLength();
            }

            @Override
            public InputStream download() throws IOException {
                var file = getFile();
                if (file.downloadUrl() != null) {
                    return URI.create(file.downloadUrl()).toURL().openStream();
                }
                var fileId = String.valueOf(file.id());
                return URI.create("https://edge.forgecdn.net/files/%s/%s/%s"
                        .formatted(fileId.substring(0, 4), fileId.substring(4), URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8)))
                        .toURL().openStream();
            }

            private synchronized File getFile() {
                while (this.file == null) {
                    try {
                        file = api.getHelper().getFiles(fileId).orElseThrow().get(0);
                    } catch (Exception ex) {
                        Main.LOGGER.error("Exception trying to get file {}... retrying in 45 seconds", fileId, ex);
                        try {
                            Thread.sleep(45 * 1000L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return file;
            }

            @Override
            public ModPlatform getPlatform() {
                return CurseForgePlatform.this;
            }

            @Override
            public String getUrl() {
                return "https://www.curseforge.com/minecraft/mc-mods/" + getMod().getSlug() + "/files/" + getId();
            }

            @Override
            public File getCachedFile() {
                return this.file;
            }

            @Override
            public void setCachedFile(File file) {
                this.file = file;
            }

            @Override
            public String toString() {
                return "CFModFile[id=" + fileId + "]";
            }
        };
    }

    private static ModLoaderType loader(ModLoader loader) {
        return switch (loader) {
            case NEOFORGE -> ModLoaderType.NEOFORGE;
            case FORGE -> ModLoaderType.FORGE;
            case FABRIC -> ModLoaderType.FABRIC;
        };
    }

    private interface CFModFile extends PlatformProjectFile {
        @Nullable
        File getCachedFile();

        void setCachedFile(File file);
    }
}
