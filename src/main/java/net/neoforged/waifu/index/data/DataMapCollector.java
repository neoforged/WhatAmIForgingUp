package net.neoforged.waifu.index.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.waifu.db.DataMapFile;
import net.neoforged.waifu.db.IndexDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataMapCollector implements DataIndexer.DataFileIndexer {
    private final List<DataMapFile> files = new ArrayList<>();

    @Override
    public List<String> getFolderNames() {
        return List.of("data_maps");
    }

    @Override
    public void accept(String namespace, String path, JsonElement file) throws IOException {
        var obj = file.getAsJsonObject();

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
                files.add(new DataMapFile(namespace + "/" + path, entries));
            }
        }
    }

    @Override
    public void commit(IndexDatabase.ModTracker modTracker) {
        modTracker.insertDataMaps(files);
    }

    static JsonElement unwrapConditionalValue(JsonElement in) {
        if (in instanceof JsonObject o) {
            var value = o.get("neoforge:value");
            if (value != null) return value;
        }
        return in;
    }
}
