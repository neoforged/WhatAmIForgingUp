package net.neoforged.waifu.util;

import com.google.common.hash.Hasher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("deprecation")
public interface Hashing {
    static HashUtil sha1() {
        return new HashUtil(com.google.common.hash.Hashing.sha1().newHasher());
    }

    record HashUtil(Hasher haser) {
        public String hash() {
            return haser.hash().toString();
        }

        public HashUtil putString(String str) {
            haser.putString(str, StandardCharsets.UTF_8);
            return this;
        }

        public HashUtil putFile(Path file) throws IOException {
            try (var is = Files.newInputStream(file)) {
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    haser.putBytes(data, 0, nRead);
                }
            }
            return this;
        }
    }
}
