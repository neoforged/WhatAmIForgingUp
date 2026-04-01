package net.neoforged.waifu.db;

import net.neoforged.waifu.platform.ModLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public interface DatabaseManager {
    IndexDatabase<?> getDatabase(String gameVersion, ModLoader loader);

    boolean exists(String gameVersion, ModLoader loader);

    DatabaseSearchHelper search(String gameVersion, ModLoader loader, Consumer<Runnable> cancellationInvokers);

    List<Version> getAllVersions();

    record Version(String gameVersion, ModLoader loader) implements Comparable<Version> {
        @Override
        public int compareTo(DatabaseManager.Version o) {
            if (Objects.equals(this.gameVersion(), o.gameVersion())) {
                return this.loader().compareTo(o.loader());
            }
            return new DefaultArtifactVersion(gameVersion).compareTo(new DefaultArtifactVersion(o.gameVersion()));
        }
    }
}
