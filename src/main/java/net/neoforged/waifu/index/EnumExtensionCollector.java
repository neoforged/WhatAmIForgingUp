package net.neoforged.waifu.index;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.waifu.db.EnumExtension;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModInfo;
import net.neoforged.waifu.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EnumExtensionCollector {
    public static List<EnumExtension> collect(ModFileInfo file) throws IOException {
        var paths = file.getMods().stream().map(ModInfo::enumExtensions)
                .filter(Objects::nonNull).map(file::getPath)
                .toList();

        if (paths.isEmpty()) return List.of();
        var lst = new ArrayList<EnumExtension>();
        for (Path path : paths) {
            read(path, lst);
        }
        return lst;
    }

    private static void read(Path path, List<EnumExtension> lst) throws IOException {
        try (var is = Files.newBufferedReader(path)) {
            var json = Utils.GSON.fromJson(is, JsonObject.class);
            var entries = json.get("entries");
            if (entries != null && entries.isJsonArray()) {
                for (JsonElement el : entries.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    var asObj = el.getAsJsonObject();
                    lst.add(new EnumExtension(
                            asObj.get("enum").getAsString(),
                            asObj.get("name").getAsString(),
                            asObj.get("constructor").getAsString(),
                            asObj.get("parameters")
                    ));
                }
            }
        }
    }
}
