package net.neoforged.waifu.meta;

import net.neoforged.waifu.util.Hashing;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public record ModFilePath(Path rootDirectory, String hash, @Nullable Path temporaryPath) {

    public static ModFilePath create(Path inZip) throws IOException {
        var hash = Hashing.sha1().putFile(inZip).hash();
        return new ModFilePath(
                FileSystems.newFileSystem(inZip).getRootDirectories().iterator().next(),
                hash, null
        );
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
