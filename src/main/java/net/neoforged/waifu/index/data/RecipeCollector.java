package net.neoforged.waifu.index.data;

import com.google.gson.JsonElement;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.db.RecipeFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RecipeCollector implements DataIndexer.DataFileIndexer {
    private final List<RecipeFile> files = new ArrayList<>();

    @Override
    public List<String> getFolderNames() {
        // TODO - make this depend on the version being indexed
        return List.of(
                "recipe",
                "recipes"
        );
    }

    @Override
    public void accept(String namespace, String path, JsonElement file) throws IOException {
        var obj = file.getAsJsonObject();

        var type = obj.remove("type");
        if (type != null) {
            files.add(new RecipeFile(
                    namespace + ":" + path,
                    TagCollector.prefixDefaultNamespace(type.getAsString()),
                    obj
            ));
        }
    }

    @Override
    public void commit(IndexDatabase.ModTracker modTracker) {
        modTracker.insertRecipes(files);
    }
}
