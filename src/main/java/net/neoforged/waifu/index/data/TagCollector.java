package net.neoforged.waifu.index.data;

import com.google.gson.JsonElement;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.db.TagFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TagCollector implements DataIndexer.DataFileIndexer {
    private final List<TagFile> tags = new ArrayList<>();

    @Override
    public List<String> getFolderNames() {
        return List.of("tags");
    }

    @Override
    public void accept(String namespace, String path, JsonElement file) throws IOException {
        var obj = file.getAsJsonObject();
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
                tags.add(new TagFile(namespace + "/" + path, replace != null && replace.getAsBoolean(), entries));
            }
        }
    }

    @Override
    public void commit(IndexDatabase.ModTracker modTracker) {
        modTracker.insertTags(tags);
    }

    static String prefixDefaultNamespace(String str) {
        if (str.indexOf(':') >= 0) return str;
        if (str.startsWith("#")) {
            return "#minecraft:" + str.substring(1);
        }
        return "minecraft:" + str;
    }
}
