package net.neoforged.waifu.db;

import com.google.gson.JsonObject;

public record RecipeFile(
        String name,
        String type,
        JsonObject value
) {
}
