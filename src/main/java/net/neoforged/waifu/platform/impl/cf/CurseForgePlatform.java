package net.neoforged.waifu.platform.impl.cf;

import com.google.common.collect.Lists;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.annotation.Nullable;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
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
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformMod;
import net.neoforged.waifu.platform.PlatformModFile;
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CurseForgePlatform implements ModPlatform {
    private final CurseForgeAPI api;

    public CurseForgePlatform(CurseForgeAPI api) {
        this.api = api;
    }

    @Override
    public String getName() {
        return "curseforge";
    }

    @Override
    public String getLogoUrl() {
        return "https://static-beta.curseforge.com/images/cf_legacy.png";
    }

    @Override
    public PlatformMod getModById(Object id) {
        try {
            return createMod(api.getHelper().getMod((int) id).orElseThrow());
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterator<PlatformMod> searchMods(String version) {
        try {
            return new MappingIterator<>(api.getHelper().paginated(q -> Requests.searchModsPaginated(ModSearchQuery.of(Constants.GameIDs.MINECRAFT)
                            .gameVersion(version).classId(6) // 6 is mods
                            .sortOrder(ModSearchQuery.SortOrder.DESCENDENT)
                            .sortField(ModSearchQuery.SortField.LAST_UPDATED)
                            .modLoaderType(ModLoaderType.NEOFORGE)
                            .paginated(q)), Function.identity())
                    .orElseThrow(), this::createMod);
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PlatformModFile> getFiles(List<Object> fileIds) {
        try {
            return api.getHelper().getFiles(fileIds.stream().mapToInt(i -> (Integer) i).toArray())
                    .map(l -> l.stream().map(f -> createFile(null, f.id(), f)).toList())
                    .orElseThrow();
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PlatformModFile> getModsInPack(PlatformModFile pack) {
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
    public List<@org.jetbrains.annotations.Nullable PlatformModFile> getFilesByFingerprint(List<ModFileInfo> files) {
        try {
            var mods = new ArrayList<PlatformModFile>(files.size());
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
    public void bulkFillData(List<PlatformModFile> files) {
        var castFiles = (List<CFModFile>) (List) new ArrayList<>(files);
        castFiles.removeIf(f -> f.getCachedFile() != null);
        var partition = Lists.partition(castFiles, 50);
        for (List<CFModFile> cfModFiles : partition) {
            try {
                var res = api.getHelper().getFiles(cfModFiles.stream().mapToInt(f -> (int) f.getId()).toArray()).orElseThrow();
                for (int i = 0; i < res.size(); i++) {
                    cfModFiles.get(i).setCachedFile(res.get(i));
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private PlatformMod createMod(Mod mod) {
        return new PlatformMod() {
            @Override
            public Object getId() {
                return mod.id();
            }

            @Override
            public String getSlug() {
                return mod.slug();
            }

            @Override
            public PlatformModFile getLatestFile(String gameVersion) {
                var idx = mod.latestFilesIndexes().stream()
                        .filter(f -> f.gameVersion().equals(gameVersion) && f.modLoader() != null && f.modLoaderType() == ModLoaderType.NEOFORGE)
                        .limit(1)
                        .findFirst()
                        .orElse(null);
                return idx == null ? null : createFile(this, idx.fileId(), null);
            }

            @Override
            public Instant getLatestReleaseDate() {
                return mod.latestFiles()
                        .stream().map(f -> Instant.parse(f.fileDate()))
                        .max(Comparator.comparing(Function.identity()))
                        .orElseThrow();
            }

            @Override
            public boolean isAvailable() {
                return mod.isAvailable();
            }
        };
    }

    private PlatformModFile createFile(@Nullable PlatformMod platformMod, int fileId, @Nullable File optionalFile) {
        return new CFModFile() {
            private File file = optionalFile;

            @Override
            public Object getModId() {
                return getFile().modId();
            }

            @Override
            public Object getId() {
                return fileId;
            }

            private PlatformMod mod = platformMod;
            @Override
            public PlatformMod getMod() {
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
        };
    }

    private interface CFModFile extends PlatformModFile {
        @Nullable
        File getCachedFile();

        void setCachedFile(File file);
    }
}
