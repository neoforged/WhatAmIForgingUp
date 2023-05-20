/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import io.github.matyrobbrt.curseforgeapi.util.gson.RecordTypeAdapterFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public record PistonMeta(List<Version> versions) {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new RecordTypeAdapterFactory())
            .create();

    public static final Path CACHE = Path.of("meta");
    public static PistonMeta data = resolveMeta(); // TODO - refresh every few minutes

    private static PistonMeta resolveMeta() {
        final var cachedPath = CACHE.resolve("piston-meta.json");
        try (final var is = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL().openStream()) {
            final var allBytes = is.readAllBytes();
            if (!Files.exists(cachedPath) || !Arrays.equals(allBytes, io(() -> Files.newInputStream(cachedPath), InputStream::readAllBytes))) {
                Files.deleteIfExists(cachedPath);
                Files.createDirectories(cachedPath.getParent());
                Files.write(cachedPath, allBytes);
            }
            try (final var reader = new InputStreamReader(new ByteArrayInputStream(allBytes))) {
                return GSON.fromJson(reader, PistonMeta.class);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static <T extends Closeable, Z> Z io(IOSupplier<T> sup, FunctionEx<T, Z> map) throws IOException {
        try (final T t = sup.get()) {
            return map.apply(t);
        }
    }

    @Nullable
    public static Version getVersion(String id) {
        return data.versions.stream().filter(it -> it.id.equals(id))
                    .findFirst().orElse(null);
    }

    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    private interface FunctionEx<T, Z> {
        Z apply(T input) throws IOException;
    }

    static final class Version {
        public String id;
        public String type;
        public String url;
        public String sha1;

        @Expose(deserialize = false)
        MetaPackage metaPackage;

        MetaPackage resolvePackage() throws IOException {
            if (metaPackage != null) return metaPackage;
            final var cachedPath = CACHE.resolve(id + ".package.json");
            try (final var is = URI.create(url).toURL().openStream()) {
                if (!Files.exists(cachedPath) || !Arrays.equals(is.readAllBytes(), io(() -> Files.newInputStream(cachedPath), InputStream::readAllBytes))) {
                    Files.deleteIfExists(cachedPath);
                    Files.createDirectories(cachedPath.getParent());
                    Files.write(cachedPath, is.readAllBytes());
                }
            }
            try (final var is = Files.newBufferedReader(cachedPath)) {
                return metaPackage = GSON.fromJson(is, MetaPackage.class);
            }
        }
    }

    static class MetaPackage {
        public Downloads downloads;

        static class Downloads {
            public Download client;
            public Download client_mappings;
            public Download server;
            public Download server_mappings;
        }

        static class Download {
            public String sha1;
            public long size;
            public String url;

            public void download(Path location) throws IOException {
                try (final InputStream is = open()) {
                    Files.deleteIfExists(location);
                    if (location.getParent() != null) Files.createDirectories(location.getParent());
                    Files.write(location, is.readAllBytes());
                }
            }

            public InputStream open() throws IOException {
                return URI.create(url).toURL().openStream();
            }
        }
    }
}
