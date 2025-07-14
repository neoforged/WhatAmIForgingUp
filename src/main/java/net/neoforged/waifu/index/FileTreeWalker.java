package net.neoforged.waifu.index;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface FileTreeWalker {
    FileTreeWalker relative(String path);

    void walkMatching(Pattern pattern, FileConsumer consumer) throws IOException;

    @FunctionalInterface
    interface FileConsumer {
        void accept(Path file, Matcher namePattern) throws IOException;
    }

    // TODO - consider storing the list of files so we don't have to walk it every time?
    static FileTreeWalker from(Path path) {
        return new FileTreeWalker() {
            @Override
            public FileTreeWalker relative(String relativePath) {
                return FileTreeWalker.from(path.resolve(relativePath));
            }

            @Override
            public void walkMatching(Pattern pattern, FileConsumer consumer) throws IOException {
                if (!Files.isDirectory(path)) return;
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        var name = path.relativize(file).toString();
                        var matcher = pattern.matcher(name);
                        if (matcher.matches()) {
                            consumer.accept(file, matcher);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        };
    }
}
