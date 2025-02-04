package net.neoforged.waifu.platform.impl.mr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformMod;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.MappingIterator;
import net.neoforged.waifu.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ModrinthPlatform implements ModPlatform {
    public static final ModrinthPlatform INSTANCE = new ModrinthPlatform();
    private final HttpClient client;

    private ModrinthPlatform() {
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "modrinth";
    }

    @Override
    public String getLogoUrl() {
        return "https://github.com/gabrielvicenteYT/modrinth-icons/blob/main/Branding/Favicon/favicon__192x192.png?raw=true";
    }

    @Override
    public PlatformMod getModById(Object id) {
        var projectId = sendRequest("/project/" + id, new TypeToken<ProjectResponse>() {}).id();
        return createMod(projectId, projectId); // we don't have a slug in the response
    }

    @Override
    public Iterator<PlatformMod> searchMods(String version, SearchSortField field) {
        record Project(String project_id, String slug) {}
        record SearchProjects(int total_hits, List<Project> hits) {}
        var indexType = switch (field) {
            case LAST_UPDATED -> "updated";
            case NEWEST_RELEASED -> "newest";
        };
        Function<Integer, SearchProjects> collector = i -> sendRequest("/search?limit=100&index=" + indexType + "&offset=" + i + "&facets=" + URLEncoder.encode("[[\"categories:neoforge\"],[\"versions:" + version + "\"],[\"project_type:mod\"]]", StandardCharsets.UTF_8), new TypeToken<SearchProjects>() {});
        return new Iterator<>() {
            private final AtomicInteger currentIndex = new AtomicInteger(-1);
            private final AtomicInteger size = new AtomicInteger();
            private volatile List<PlatformMod> currentResponse;
            private final AtomicInteger currentListIndex = new AtomicInteger(-1);

            {
                var baseResponse = collector.apply(0);
                currentResponse = baseResponse.hits.stream()
                        .map(p -> createMod(p.project_id(), p.slug()))
                        .toList();
                size.set(baseResponse.total_hits());
            }

            @Override
            public boolean hasNext() {
                return currentIndex.get() + 1 < size.get();
            }

            @Override
            public PlatformMod next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more elements left");
                }
                if (currentListIndex.get() + 1 >= currentResponse.size()) {
                    requery();
                }

                currentIndex.getAndIncrement();
                return currentResponse.get(currentListIndex.incrementAndGet());
            }

            private synchronized void requery() {
                var res = collector.apply(currentIndex.get() + 1);
                size.set(res.total_hits());
                currentResponse = res.hits.stream()
                        .map(p -> createMod(p.project_id(), p.slug()))
                        .toList();
                currentListIndex.set(-1);
            }
        };
    }

    @Override
    public List<PlatformModFile> getFiles(List<Object> fileIds) {
        var array = new JsonArray();
        for (Object fileId : fileIds) {
            array.add(fileId.toString());
        }
        var versions = sendRequest("/versions?ids=" + URLEncoder.encode(Utils.GSON.toJson(array), StandardCharsets.UTF_8), new TypeToken<List<Version>>() {});
        return versions.stream().map(v -> createModFile(null, v)).toList();
    }

    @Override
    public List<PlatformModFile> getModsInPack(PlatformModFile pack) {
        throw new RuntimeException("unsupported");
    }

    @Override
    public List<@Nullable PlatformModFile> getFilesByFingerprint(List<ModFileInfo> files) {
        var mods = new ArrayList<PlatformModFile>(files.size());
        for (int i = 0; i < files.size(); i++) mods.add(null);

        var hashes = new JsonArray();
        for (ModFileInfo file : files) {
            hashes.add(file.getFileHash());
        }

        var req = new JsonObject();
        req.addProperty("algorithm", "sha1");
        req.add("hashes", hashes);
        var response = sendPostRequest("/version_files", req, new TypeToken<Map<String, Version>>() {});

        for (int i = 0; i < files.size(); i++) {
            var fromHash = response.get(files.get(i).getFileHash());
            if (fromHash != null) {
                mods.set(i, createModFile(null, fromHash));
            }
        }

        return mods;
    }

    @Override
    public int pageLimit() {
        return 100;
    }

    private PlatformMod createMod(String id, String slug) {
        return new PlatformMod() {
            @Override
            public Object getId() {
                return id;
            }

            @Override
            public String getSlug() {
                return slug;
            }

            private volatile List<Version> versions;

            @Override
            public PlatformModFile getLatestFile(String gameVersion) {
                var file = getVersions().stream()
                        .filter(v -> v.loaders.contains("neoforge") && v.game_versions.contains(gameVersion))
                        .findFirst()
                        .orElse(null);
                return file == null ? null : createModFile(this, file);
            }

            @Override
            public Iterator<PlatformModFile> getAllFiles() {
                return new MappingIterator<>(getVersions().iterator(), version -> createModFile(this, version));
            }

            @Override
            public Instant getLatestReleaseDate() {
                return getVersions().get(0).date_published;
            }

            private synchronized List<Version> getVersions() {
                if (versions == null) {
                    versions = sendRequest("/project/" + id + "/version", new TypeToken<>() {});
                    if (versions == null) {
                        versions = List.of();
                    }
                }
                return versions;
            }
        };
    }

    private PlatformModFile createModFile(@Nullable PlatformMod inMod, Version version) {
        var downloadFile = version.files.size() == 1 ? version.files.get(0) : version.files.stream().filter(Version.File::primary).findFirst().orElse(null);
        if (downloadFile == null) return null;
        return new PlatformModFile() {
            private PlatformMod mod = inMod;

            @Override
            public Object getModId() {
                return version.project_id;
            }

            @Override
            public Object getId() {
                return version.id();
            }

            @Override
            public synchronized PlatformMod getMod() {
                if (mod == null) {
                    mod = createMod(version.project_id, version.project_id);
                }
                return mod;
            }

            @Override
            public String getHash() {
                return downloadFile.hashes.sha1();
            }

            @Override
            public long getFileLength() {
                return downloadFile.size;
            }

            @Override
            public InputStream download() throws IOException {
                return URI.create(downloadFile.url).toURL().openStream();
            }

            @Override
            public ModPlatform getPlatform() {
                return ModrinthPlatform.this;
            }

            @Override
            public String getUrl() {
                return "https://modrinth.com/mod/" + getModId() + "/version/" + getId();
            }

            @Override
            public String toString() {
                return "MRModFile[id=" + version.id() + ", project=" + version.project_id() + "]";
            }
        };
    }

    private <T> T sendRequest(String subpath, TypeToken<T> type) {
        var uri = URI.create("https://api.modrinth.com/v2" + subpath);
        return send(HttpRequest.newBuilder().uri(uri), type);
    }

    private <T> T sendPostRequest(String subpath, JsonObject body, TypeToken<T> type) {
        var uri = URI.create("https://api.modrinth.com/v2" + subpath);
        return send(HttpRequest.newBuilder()
                .uri(uri).POST(HttpRequest.BodyPublishers.ofString(Utils.GSON.toJson(body)))
                .header("Content-Type", "application/json"), type);
    }

    private <T> T send(HttpRequest.Builder builder, TypeToken<T> type) {
        HttpResponse<String> res;
        try {
            res = client.send(builder
                    .header("User-Agent", "neoforged/WhatAmIForgingUp (neoforged.net)")
                    .build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        var remaining = res.headers().firstValue("x-ratelimit-remaining").orElse(null);
        if (remaining != null && Integer.parseInt(remaining) <= 0) {
            try {
                Thread.sleep(Long.parseLong(res.headers().firstValue("x-ratelimit-reset").orElse("60")) * 1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return Utils.GSON.fromJson(res.body(), type);
    }

    private record ProjectResponse(String id) {}
    private record Version(String id, String project_id, List<String> game_versions, List<String> loaders, Instant date_published, List<File> files) {

        private record File(Hashes hashes, String url, boolean primary, long size) {

        }

        private record Hashes(String sha1) {}
    }
}
