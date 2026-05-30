package net.neoforged.waifu.platform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public interface PlatformProjectFile {
    Object getProjectId();

    Object getId();

    PlatformProject getMod();

    String getHash();

    long getFileLength();

    ModPlatform getPlatform();

    String getUrl();

    String getDownloadUrl();

    default InputStream download() throws IOException {
        return URI.create(getDownloadUrl()).toURL().openStream();
    }
}
