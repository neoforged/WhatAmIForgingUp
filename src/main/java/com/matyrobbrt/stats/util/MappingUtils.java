package com.matyrobbrt.stats.util;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MappingUtils {
    public static final Path CACHE = Path.of("mappings");

    public static IMappingFile srgToMoj(String mcVersion) throws IOException {
        final Path path = CACHE.resolve(mcVersion + ".srg");
        if (Files.exists(path)) return IMappingFile.load(path.toFile());

        final String downloadUrl = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/%s/mcp_config-%s.zip".formatted(mcVersion, mcVersion);
        byte[] srgMappingsData = new byte[0];
        try (final ZipInputStream is = new ZipInputStream(new URL(downloadUrl).openStream())) {
            ZipEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals("config/joined.tsrg")) {
                    srgMappingsData = is.readAllBytes();
                }
            }
        }

        final IMappingFile deobfToObf;
        try (final InputStream is = PistonMeta.getVersion(mcVersion).resolvePackage().downloads.client_mappings.open()) {
            deobfToObf = IMappingFile.load(is);
        }
        final IMappingFile obfToSrg = IMappingFile.load(new ByteArrayInputStream(srgMappingsData));
        final IMappingFile deobfToSrg = deobfToObf.rename(new IRenamer() {
            @Override
            public String rename(IMappingFile.IField value) {
                return obfToSrg.getClass(value.getParent().getMapped()).getField(value.getMapped()).getMapped();
            }

            @Override
            public String rename(IMappingFile.IMethod value) {
                return obfToSrg.getClass(value.getParent().getMapped()).getMethod(value.getMapped(), value.getMappedDescriptor()).getMapped();
            }

            @Override
            public String rename(IMappingFile.IClass value) {
                return obfToSrg.getClass(value.getMapped()).getMapped();
            }
        });
        final IMappingFile mappingFile = deobfToSrg.rename(new IRenamer() {
            @Override
            public String rename(IMappingFile.IClass value) {
                return value.getOriginal();
            }
        }).reverse();
        mappingFile.write(path, IMappingFile.Format.TSRG, false);
        return mappingFile;
    }
}
