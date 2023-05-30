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
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.matyrobbrt.curseforgeapi.util.gson.RecordTypeAdapterFactory;
import net.minecraftforge.waifu.collect.ModPointer;
import net.minecraftforge.waifu.collect.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ModCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCollector.class);
    private static final TomlParser PARSER = new TomlParser();
    public static final Path DOWNLOAD_CACHE = BotMain.ROOT.resolve("cfCache");
    private final CurseForgeAPI api;

    private final Set<String> jarJars = new HashSet<>();
    private final Map<ModPointer, SecureJar> jars = new HashMap<>();

    public ModCollector(CurseForgeAPI api) {
        this.api = api;
    }

    public Map<ModPointer, SecureJar> getJarsToProcess() {
        return jars;
    }

    public void fromModpack(int packId, int fileId, ProgressMonitor monitor) throws CurseForgeException, IOException, URISyntaxException {
        final var response = api.getHelper().getModFile(packId, fileId);
        if (response.isEmpty()) {
            LOGGER.error("Could not query file with ID {}. Status code: {}", fileId, response.stream());
            return;
        }
        fromModpack(response.get(), monitor);
    }

    public void fromModpack(File packFile, ProgressMonitor monitor) throws CurseForgeException, IOException, URISyntaxException {
        final Path modpackFile = download(packFile, zipEntry -> zipEntry.getName().equals("manifest.json"));
        if (modpackFile == null) return;

        final Manifest mf;
        try (final ZipFile zip = new ZipFile(modpackFile.toFile())) {
            final var mfEntry = zip.getEntry("manifest.json");
            if (mfEntry != null) {
                try (final Reader reader = new InputStreamReader(zip.getInputStream(mfEntry))) {
                    mf = GSON.fromJson(reader, Manifest.class);
                }
            } else {
                return;
            }
        }

        final Response<List<File>> fileQuery = api.getHelper()
                .getFiles(mf.files.stream().mapToInt(FilePointer::fileID).toArray());
        if (fileQuery.isEmpty()) {
            LOGGER.error("Could not query files of modpack {}. Status code was {}", packFile.modId(), fileQuery.getStatusCode());
            return;
        }

        final List<File> files = fileQuery.get()
                .stream().filter(f -> f.downloadUrl() != null)
                .filter(BotMain.distinct(File::id))
                .toList();
        monitor.setDownloadTarget(files.size());

        if (!files.isEmpty()) {
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
    }

    public void considerFile(int projectId, int fileId) throws CurseForgeException, IOException, URISyntaxException {
        final var response = api.getHelper().getModFile(projectId, fileId);
        if (response.isEmpty()) {
            LOGGER.error("Could not query file with ID {}. Status code: {}", fileId, response.stream());
            return;
        }
        considerFile(response.get());
    }

    public void considerFile(File file) throws IOException, URISyntaxException {
        final Path downloaded = download(file);
        if (downloaded == null) return;
        consider(SecureJar.from(downloaded), new FilePointer(file.modId(), file.id()));
    }

    @Nullable
    public Path download(File file) throws IOException, URISyntaxException {
        return download(file, entry -> entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/jarjar/") || entry.getName().equals("META-INF/mods.toml"));
    }

    @Nullable
    public Path download(File file, Predicate<ZipEntry> shouldKeep) throws IOException, URISyntaxException {
        if (file.downloadUrl() == null) return null;
        final String ext = getExtension(file.downloadUrl());
        final Path path = DOWNLOAD_CACHE.resolve(file.modId() + "/" + file.id() + (ext.isBlank() ? "" : "." + ext));
        Files.createDirectories(path.getParent());
        final URL url = createURL(file.downloadUrl());
        switch (ext) {
            case "jar", "zip" -> {
                try (final ZipInputStream in = new ZipInputStream(url.openStream());
                     final ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
                    transferStripping(out, in, shouldKeep);
                }
            }
            default -> {
                try (final InputStream in = url.openStream()) {
                    Files.write(path, in.readAllBytes());
                }
            }
        }
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

    private static URL createURL(String url) throws IOException, URISyntaxException {
        final int findex = url.indexOf('/', 8); // Get rid of the https://
        return new URI(
                "https",
                url.substring(8, findex),
                url.substring(findex),
                null
        ).toURL();
    }

    private static final Random RANDOM = new Random();
    private static void transferStripping(ZipOutputStream out, ZipInputStream in, Predicate<ZipEntry> predicate) throws IOException {
        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
            if (predicate.test(entry)) {
                if (entry.getName().startsWith("META-INF/jarjar/") && entry.getName().endsWith(".jar")) {
                    final Path temp = Files.createTempFile("jij-" + RANDOM.nextInt(100_000_00), ".jar");
                    try (final OutputStream outT = Files.newOutputStream(temp)) {
                        in.transferTo(outT);
                    }

                    try (final ZipFile zip = new ZipFile(temp.toFile())) {
                        final ZipEntry modsDotToml = zip.getEntry("META-INF/mods.toml");
                        out.putNextEntry(entry);
                        final ZipOutputStream zout = new ZipOutputStream(out);
                        try (final ZipInputStream is = new ZipInputStream(Files.newInputStream(temp))) {
                            transferStripping(zout, is, modsDotToml == null ? e -> e.getName().startsWith("META-INF/jarjar/") : predicate);
                        }
                        zout.finish();
                        out.closeEntry();
                    }

                    Files.delete(temp);
                } else {
                    out.putNextEntry(entry);
                    in.transferTo(out);
                    out.closeEntry();
                }
            }
        }
    }

    private static String getExtension(String str) {
        final int idx = str.lastIndexOf('.') + 1;
        if (idx > str.length()) {
            return "";
        }
        return str.substring(idx);
    }
}
