package net.neoforged.waifu.meta;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ModInfo(String modId, DefaultArtifactVersion version, String updateJSONURL, String authors, String displayName, @Nullable String enumExtensions) {
    public static ModInfo create(CommentedConfig config, String jarVersion) {
        var modId = config.<String>get("modId");
        return new ModInfo(
                modId,
                new DefaultArtifactVersion(config.getOrElse("version", "0.0NONE")
                    .replace("${file.jarVersion}", jarVersion)),
                config.get("updateJSONURL"),
                Optional.ofNullable(config.get("authors")).map(Object::toString).orElse(null),
                config.getOrElse("displayName", modId),
                config.get("enumExtensions")
        );
    }
}
