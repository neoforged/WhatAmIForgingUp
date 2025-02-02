package net.neoforged.waifu.meta;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class LibraryModFileInfo extends BaseModFileInfo implements ModFileInfo {
    private final Type type;
    private final DefaultArtifactVersion version;
    private final String coordinates;
    private final String name;
    LibraryModFileInfo(ModFilePath path, Type type, String version, Manifest manifest, @Nullable String coordinates) throws IOException {
        super(path);
        this.type = type;
        this.version = new DefaultArtifactVersion(version);
        this.coordinates = coordinates;

        this.name = computeName(path, manifest, coordinates);
    }

    private static String computeName(ModFilePath path, Manifest man, @Nullable String coords) {
        if (coords != null) return coords;
        var fromMan = man.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        if (fromMan != null) return fromMan;
        try {
            try (var str = Files.walk(path.rootDirectory())
                    .filter(f -> f.toString().endsWith(".class"))) {
                return str.map(p -> {
                            var spl = p.toString().split("/");
                            var pkg = new StringBuilder();
                            for (int i = 0; i < spl.length - 1; i++) {
                                if (i != 0) pkg.append(".");
                                pkg.append(spl[i]);
                            }
                            return pkg.toString();
                        })
                        .findFirst()
                        .orElse("");
            }
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public @Nullable String getMavenCoordinates() {
        return coordinates;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public DefaultArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<ModInfo> getMods() {
        return List.of();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public @Nullable ModFileMetadata getMetadata() {
        return null;
    }
}
