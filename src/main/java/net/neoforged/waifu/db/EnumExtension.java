package net.neoforged.waifu.db;

import com.google.gson.JsonElement;

public record EnumExtension(String enumName, String name, String constructor, JsonElement parameters) {
}
