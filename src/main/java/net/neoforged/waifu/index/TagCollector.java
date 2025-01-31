package net.neoforged.waifu.index;

import com.google.gson.JsonObject;
import net.neoforged.waifu.db.TagFile;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class TagCollector {
    public static List<TagFile> collect(Path baseDir) throws IOException {
        if (!Files.isDirectory(baseDir)) return List.of();

        var tags = new ArrayList<TagFile>();

        try (var namespaceStream = Files.list(baseDir).filter(Files::isDirectory)) {
            var itr = namespaceStream.iterator();
            while (itr.hasNext()) {
                var namespaceDir = itr.next();
                if (!Files.isDirectory(namespaceDir)) continue;

                var namespace = namespaceDir.getFileName().toString();
                var tagsSubPath = namespaceDir.resolve("tags");
                if (Files.isDirectory(tagsSubPath)) {
                    Files.walkFileTree(tagsSubPath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            var fileName = tagsSubPath.relativize(file).toString();
                            if (fileName.endsWith(".json")) {
                                process(namespace, fileName.substring(0, fileName.length() - 5), file, tags);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        }

        return tags;
    }

    private static void process(String namespace, String name, Path file, List<TagFile> tags) throws IOException {
        try (var is = Files.newBufferedReader(file)) {
            var obj = Utils.GSON.fromJson(is, JsonObject.class);
            var values = obj.getAsJsonArray("values");
            if (values != null) {
                var entries = new ArrayList<String>();
                values.forEach(element -> {
                    if (element.isJsonPrimitive()) {
                        entries.add(prefixDefaultNamespace(element.getAsString()));
                    } else if (element.isJsonObject()) {
                        var asObj = element.getAsJsonObject();
                        var id = asObj.getAsJsonPrimitive("id");
                        if (id != null) {
                            entries.add(prefixDefaultNamespace(id.getAsString()));
                        }
                    }
                });

                var replace = obj.getAsJsonPrimitive("replace");

                if (!entries.isEmpty()) {
                    tags.add(new TagFile(namespace + "/" + name, replace != null && replace.getAsBoolean(), entries));
                }
            }
        } catch (Exception ignored) {

        }
    }

    private static String prefixDefaultNamespace(String str) {
        if (str.indexOf(':') >= 0) return str;
        if (str.startsWith("#")) {
            return "#minecraft:" + str.substring(1);
        }
        return "minecraft:" + str;
    }
}
