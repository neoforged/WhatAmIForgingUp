package net.neoforged.waifu.db;

import com.google.gson.JsonElement;

import java.util.List;

/**
 * @param name this name also contains the registry
 */
public record DataMapFile(
        String name,
        List<DataMapEntry> entries
) {
    public record DataMapEntry(
            String key,
            boolean replace,
            JsonElement value
    ) {}
}
