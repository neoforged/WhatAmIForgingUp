package net.neoforged.waifu.db;

import net.neoforged.waifu.platform.ModLoader;

public interface DatabaseManager {
    IndexDatabase<?> getDatabase(String gameVersion, ModLoader loader);

    boolean exists(String gameVersion, ModLoader loader);

    DatabaseSearchHelper search(String gameVersion, ModLoader loader);
}
