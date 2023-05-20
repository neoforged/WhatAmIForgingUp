/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.jarhandling.SecureJar;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.matyrobbrt.curseforgeapi.util.gson.RecordTypeAdapterFactory;
import net.minecraftforge.waifu.collect.ModPointer;
import net.minecraftforge.waifu.collect.ProgressMonitor;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModCollector {
    private static final TomlParser PARSER = new TomlParser();
    public static final Path DOWNLOAD_CACHE = Path.of("cfCache");
    private final CurseForgeAPI api;

    private final Set<String> jarJars = new HashSet<>();
    private final Map<ModPointer, SecureJar> jars = new HashMap<>();

    public ModCollector(CurseForgeAPI api) {
        this.api = api;
    }

    public Map<ModPointer, SecureJar> getJarsToProcess() {
        return jars;
    }

    public void fromModpack(int packId, int fileId, ProgressMonitor monitor) throws CurseForgeException, IOException {
        fromModpack(api.getHelper().getModFile(packId, fileId).orElseThrow(), monitor);
    }

    public void fromModpack(File packFile, ProgressMonitor monitor) throws CurseForgeException, IOException {
        final Path modpackFile = download(packFile);
        if (modpackFile == null) return;

        final Manifest mf;
        try (final FileSystem zip = FileSystems.newFileSystem(modpackFile)) {
            final Path manifest = zip.getPath("manifest.json");
            if (!Files.exists(manifest)) return;
            try (final BufferedReader reader = Files.newBufferedReader(manifest)) {
                mf = GSON.fromJson(reader, Manifest.class);
            }
        }

        final List<File> files = api.getHelper()
                .getFiles(mf.files.stream().mapToInt(FilePointer::fileID).toArray())
                .orElseThrow()
                .stream().filter(f -> f.downloadUrl() != null)
                .filter(BotMain.distinct(File::id))
                .toList();
        monitor.setDownloadTarget(files.size());
        try (final ExecutorService executor = Executors.newFixedThreadPool(3, Thread.ofPlatform()
                .name("mod-downloader", 0)
                .daemon(true)
                .factory())) {
            for (final File file : files) {
                executor.submit(() -> {
                    try {
                        considerFile(file);
                    } finally {
                        monitor.downloadEnded(file);
                    }
                    return null;
                });
            }
        }
    }

    public void considerFile(int projectId, int fileId) throws CurseForgeException, IOException {
        considerFile(api.getHelper().getModFile(projectId, fileId).orElseThrow());
    }

    public void considerFile(File file) throws IOException {
        final Path downloaded = download(file);
        if (downloaded == null) return;
        consider(SecureJar.from(downloaded), new FilePointer(file.modId(), file.id()));
    }

    @Nullable
    public Path download(File file) throws IOException {
        if (file.downloadUrl() == null) return null;
        final Path path = DOWNLOAD_CACHE.resolve(file.modId() + "/" + file.id() + file.downloadUrl().substring(file.downloadUrl().lastIndexOf('.')));
        if (Files.exists(path)) {
            if (Files.size(path) == file.fileLength()) { // TODO - hashes
                return path;
            }
        }
        file.download(path);
        return path;
    }

    public void consider(SecureJar jar, @Nullable FilePointer file) throws IOException {
        final String modId = getModId(jar);
        if (modId != null) {
            if (file == null) {
                jars.put(new ModPointer(modId), jar);
            } else {
                jars.put(new ModPointer(modId, file.projectID, file.fileID), jar);
            }
        }
        collectJiJFrom(jar);
    }

    public void collectJiJFrom(SecureJar secureJar) throws IOException {
        final Path path = secureJar.getPath("META-INF/jarjar/metadata.json");
        if (Files.exists(path)) {
            final JsonArray array = new Gson().fromJson(
                    Files.newBufferedReader(path), JsonObject.class
            ).getAsJsonArray("jars");

            for (final JsonElement element : array) {
                final JsonObject obj = (JsonObject) element;
                final JsonObject identifier = obj.getAsJsonObject("identifier");

                final String id = identifier.get("group").getAsString() + ":" + identifier.get("artifact").getAsString();
                if (!jarJars.add(id)) continue;

                final SecureJar jar = SecureJar.from(secureJar.getPath(obj.get("path").getAsString()));
                consider(jar, null);
            }
        }
    }

    @Nullable
    public static String getModId(SecureJar jar) throws IOException {
        final Path path = jar.getPath("META-INF", "mods.toml");
        if (Files.exists(path)) {
            final CommentedConfig config = PARSER.parse(Files.newBufferedReader(path));
            final List<CommentedConfig> mods = config.get("mods");
            if (!mods.isEmpty()) {
                return mods.get(0).get("modId");
            }
        }
        return null;
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new RecordTypeAdapterFactory())
            .create();
    record FilePointer(int projectID, int fileID) {}
    record Manifest(List<FilePointer> files) {}
}
