package net.neoforged.waifu.util;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    public static final Gson GSON = new Gson();
    public static final TomlParser TOML = new TomlParser();

    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        CompletableFuture<Void> allFuturesResult = CompletableFuture.allOf(futuresList.toArray(CompletableFuture[]::new));
        return allFuturesResult.thenApply(v ->
                futuresList.stream().map(CompletableFuture::join).toList()
        );
    }

    public static <E extends Throwable> void sneakyThrow(Throwable ex) throws E {
        throw (E) ex;
    }

    public static <T> T getJson(URI uri, Class<T> type) {
        try (var reader = new InputStreamReader(uri.toURL().openStream())) {
            return GSON.fromJson(reader, type);
        } catch (IOException ex) {
            sneakyThrow(ex);
            throw null; // will never reach
        }
    }

    public static Path download(URI uri, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (var is = uri.toURL().openStream(); var os = Files.newOutputStream(path)) {
            is.transferTo(os);
        }
        return path;
    }

    public static Path downloadFromZip(URI uri, String name, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (var is = new ZipInputStream(uri.toURL().openStream());
             var os = Files.newOutputStream(path)) {
            ZipEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    is.transferTo(os);
                    break;
                }
            }
        }
        return path;
    }

}
