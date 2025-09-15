package net.neoforged.waifu.util;

import com.google.common.hash.Hasher;
import org.eclipse.jetty.util.annotation.ManagedObject;

import javax.annotation.WillClose;
import java.io.IOException;
import java.io.InputStream;
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
            return putStream(Files.newInputStream(file));
        }

        public HashUtil putStream(@WillClose InputStream stream) throws IOException {
            try (InputStream st = stream) {
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = st.read(data, 0, data.length)) != -1) {
                    haser.putBytes(data, 0, nRead);
                }
            }
            return this;
        }
    }
}
