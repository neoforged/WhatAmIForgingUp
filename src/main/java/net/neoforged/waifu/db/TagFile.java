package net.neoforged.waifu.db;

import java.util.List;

/**
 * @param name this name also contains the registry
 */
public record TagFile(
        String name,
        boolean replace,
        List<String> entries
) {

}
