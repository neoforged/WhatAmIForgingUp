package net.neoforged.waifu.db;

import com.google.gson.JsonElement;

public record JsonFile(String path, JsonElement content) {
}
