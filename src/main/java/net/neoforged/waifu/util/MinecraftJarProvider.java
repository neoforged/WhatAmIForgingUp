package net.neoforged.waifu.util;

import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.Transformer;
import net.neoforged.srgutils.IMappingFile;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFilePath;
import net.neoforged.waifu.meta.ModFileReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class MinecraftJarProvider {
    private static final Path CACHE = Main.CACHE.resolve("loader/minecraft");
    public static List<ModFileInfo> provide(String mcVersion) throws IOException {
        var pkg = MinecraftMetaUtils.getVersion(mcVersion).get();

        var rawMc = CACHE.resolve(mcVersion + "-raw.jar");
        pkg.download("client").downloadTo(rawMc);

        Path finalMcJar = rawMc;

        if (pkg.download("client_mappings") != null) {
            // Remap the jar if we're trying to index an obfuscated version (prior to 26.1)
            var remapped = CACHE.resolve(mcVersion + ".jar");
            if (!Files.exists(remapped)) {
                var namedToObf = Utils.read(pkg.download("client_mappings").url(), IMappingFile::load);

                Renamer.builder()
                        .logger(s -> {})
                        .add(Transformer.renamerFactory(namedToObf.reverse(), false))
                        .add(Transformer.recordFixerFactory())
                        .build()
                        .run(rawMc.toFile(), remapped.toFile());
            }

            finalMcJar = remapped;
        }

        var mcMod = Objects.requireNonNull(ModFileReader.LIBRARY.read(ModFilePath.create(finalMcJar), "net.minecraft:minecraft", mcVersion));

        return List.of(mcMod);
    }
}
