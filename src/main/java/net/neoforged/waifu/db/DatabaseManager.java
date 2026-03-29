package net.neoforged.waifu.db;

import net.neoforged.waifu.platform.ModLoader;

import java.util.List;
import java.util.function.Consumer;

public interface DatabaseManager {
    IndexDatabase<?> getDatabase(String gameVersion, ModLoader loader);

    boolean exists(String gameVersion, ModLoader loader);

    DatabaseSearchHelper search(String gameVersion, ModLoader loader, Consumer<Runnable> cancellationInvokers);

    List<Version> getAllVersions();

    record Version(String gameVersion, ModLoader loader) {}
}
