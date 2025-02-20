package net.neoforged.waifu.util;

import com.electronwill.nightconfig.core.io.ConfigWriter;
import com.electronwill.nightconfig.json.MinimalJsonWriter;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import net.neoforged.waifu.Main;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Utils {
    public static final Thread.UncaughtExceptionHandler LOG_EXCEPTIONS = (t, e) -> Main.LOGGER.error("Thread {} threw uncaught exception: ", t, e);
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> Instant.parse(json.getAsString()))
            .create();
    public static final TomlParser TOML = new TomlParser();
    public static final ConfigWriter JSON_NC = new MinimalJsonWriter();

    @Nullable
    public static final String VERSION = Utils.class.getPackage().getImplementationVersion();

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
        try (var os = Files.newOutputStream(path)) {
            readFromZip(uri, name, is -> is.transferTo(os));
        }
        return path;
    }

    public static <R> R readFromZip(URI uri, String name, ThrowingFunction<InputStream, R, IOException> func) throws IOException {
        try (var is = new ZipInputStream(uri.toURL().openStream())) {
            ZipEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return func.apply(is);
                }
            }
        }
        return null;
    }

    public static <R> R read(URI uri, ThrowingFunction<InputStream, R, IOException> func) throws IOException {
        try (var is = uri.toURL().openStream()) {
            return func.apply(is);
        }
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

    public static ZipEntry copyEntry(ZipEntry entry) {
        var newEntry = new ZipEntry(entry.getName());
        newEntry.setTime(628041600000L);
        return newEntry;
    }

    public static byte[] createCleanZip(InputStream is) throws IOException {
        var bos = new ByteArrayOutputStream();
        var zos = new ZipOutputStream(bos);
        try (var zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(copyEntry(entry));
                if (entry.getName().equals(JarFile.MANIFEST_NAME)) {
                    var man = new Manifest();
                    man.read(zis);
                    // Strip Implementation-Timestamp and co which prevent the jars from being the same (hopefully)
                    man.getMainAttributes().entrySet().removeIf(e -> e.getKey() instanceof Attributes.Name nm && nm.toString().toLowerCase(Locale.ROOT).endsWith("timestamp"));
                    man.write(zos);
                } else {
                    zis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    public static String tomlToJson(String toml) {
        return JSON_NC.writeToString(TOML.parse(toml));
    }
}
