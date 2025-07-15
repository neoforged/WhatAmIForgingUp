package net.neoforged.waifu.index;

import com.google.gson.JsonObject;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.db.TagFile;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TagCollector implements ModFileIndexer {
    @Override
    public Consumer<IndexDatabase.ModTracker> collectAndPrepareUpsert(ModFileInfo modFile, FileTreeWalker walker) throws IOException {
        List<TagFile> tags = new ArrayList<>();
        walker.relative("data").walkMatching(Pattern.compile("(?<namespace>.*)/tags/(?<path>.*)\\.json"), (file, matcher) -> {
            try (var is = Files.newBufferedReader(file)) {
                var obj = Utils.GSON.fromJson(is, JsonObject.class);
                var values = obj.getAsJsonArray("values");
                if (values != null) {
                    var entries = new ArrayList<String>(values.size());
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
                        tags.add(new TagFile(matcher.group(1) + "/" + matcher.group(2), replace != null && replace.getAsBoolean(), entries));
                    }
                }
            } catch (Exception ignored) {

            }
        });

        return modTracker -> modTracker.insertTags(tags);
    }

    static String prefixDefaultNamespace(String str) {
        if (str.indexOf(':') >= 0) return str;
        if (str.startsWith("#")) {
            return "#minecraft:" + str.substring(1);
        }
        return "minecraft:" + str;
    }
}
