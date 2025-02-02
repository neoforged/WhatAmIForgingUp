package net.neoforged.waifu.util;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import net.neoforged.waifu.Main;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    public static final Thread.UncaughtExceptionHandler LOG_EXCEPTIONS = (t, e) -> Main.LOGGER.error("Thread {} threw uncaught exception: ", t, e);
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> Instant.parse(json.getAsString()))
            .create();
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

    public record RefLog(String commit, String message) {
        public String getDiscordReference() {
            return "[" + message + "](<https://github.com/neoforged/whatamiforgingup/commit/" + commit + ">)";
        }
    }
    public static List<RefLog> getCommits() {
        try (var is = Utils.class.getResourceAsStream("/gitlog")) {
            var lines = new String(is.readAllBytes()).split("\n");
            return Arrays.stream(lines)
                    .filter(s -> !s.isBlank())
                    .map(s -> {
                        var spl = s.split(" ", 2);
                        return new RefLog(spl[0], spl[1]);
                    })
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
