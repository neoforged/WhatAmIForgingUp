package net.neoforged.waifu.platform.impl.cf;

import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.annotation.Nullable;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.HashAlgo;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileHash;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
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
                return createFile(this, mod.latestFilesIndexes().stream()
                        .filter(f -> f.gameVersion().equals(gameVersion) && f.modLoader() != null && f.modLoaderType() == ModLoaderType.NEOFORGE)
                        .limit(1)
                        .findFirst()
                        .orElseThrow()
                        .fileId(), null);
            }
        };
    }

    private PlatformModFile createFile(@Nullable PlatformMod platformMod, int fileId, @Nullable File optionalFile) {
        return new PlatformModFile() {
            private File file = optionalFile;

            @Override
            public Object getModId() {
                return file.modId();
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
                        mod = createMod(api.getHelper().getMod(file.modId()).orElseThrow());
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
                if (this.file == null) {
                    try {
                        file = api.getHelper().getModFile((Integer) mod.getId(), fileId).orElseThrow();
                    } catch (CurseForgeException e) {
                        throw new RuntimeException(e);
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
        };
    }
}
