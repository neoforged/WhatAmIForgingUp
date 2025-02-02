package net.neoforged.waifu.meta;

import net.neoforged.waifu.util.Hashing;
import net.neoforged.waifu.util.MurmurHash2;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public record ModFilePath(Path physicalLocation, Path rootDirectory, String hash, @Nullable Path temporaryPath) {

    public static ModFilePath create(Path inZip) throws IOException {
       return create(inZip, null);
    }

    public static ModFilePath create(Path inZip, @Nullable Path tempPath) throws IOException {
        var hash = Hashing.sha1().putFile(inZip).hash();
        return new ModFilePath(
                inZip, FileSystems.newFileSystem(inZip).getRootDirectories().iterator().next(),
                hash, tempPath
        );
    }

    public long computeMurmur2() throws IOException {
        return MurmurHash2.hash(MurmurHash2.normalizeByteArray(Files.readAllBytes(physicalLocation)));
    }

    void close() throws IOException {
        rootDirectory.getFileSystem().close();
        if (temporaryPath != null) {
            Files.deleteIfExists(temporaryPath);
        }
    }

    public Path resolve(String name) {
        return rootDirectory.resolve(name);
    }
}
