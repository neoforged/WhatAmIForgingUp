package net.neoforged.waifu.index;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.waifu.db.DataMapFile;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class DataMapCollector implements ModFileIndexer {
    @Override
    public Consumer<IndexDatabase.ModTracker> collectAndPrepareUpsert(ModFileInfo modFile, FileTreeWalker walker) throws IOException {
        List<DataMapFile> files = new ArrayList<>();

        walker.relative("data").walkMatching(Pattern.compile("(?<namespace>.*)/data_maps/(?<path>.*)\\.json"), (file, matcher) -> {
            try (var is = Files.newBufferedReader(file)) {
                var obj = Utils.GSON.fromJson(is, JsonObject.class);
                boolean defaultReplace = obj.has("replace") && obj.get("replace").getAsBoolean();
                var values = obj.getAsJsonObject("values");
                if (values != null) {
                    var entries = new ArrayList<DataMapFile.DataMapEntry>(values.size());
                    for (var set : values.entrySet()) {
                        var key = TagCollector.prefixDefaultNamespace(set.getKey());
                        var value = set.getValue();
                        if (value instanceof JsonObject o && o.has("value")) {
                            entries.add(new DataMapFile.DataMapEntry(key, defaultReplace || (o.has("replace") && o.get("replace").getAsBoolean()), unwrapConditionalValue(o.get("value"))));
                        } else {
                            entries.add(new DataMapFile.DataMapEntry(key, defaultReplace, unwrapConditionalValue(value)));
                        }
                    }

                    if (!entries.isEmpty()) {
                        files.add(new DataMapFile(matcher.group(1) + "/" + matcher.group(2), entries));
                    }
                }
            } catch (Exception ignored) {

            }
        });

        if (files.isEmpty()) return null;
        return modTracker -> modTracker.insertDataMaps(files);
    }

    static JsonElement unwrapConditionalValue(JsonElement in) {
        if (in instanceof JsonObject o) {
            var value = o.get("neoforge:value");
            if (value != null) return value;
        }
        return in;
    }
}
