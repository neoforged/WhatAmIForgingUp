package net.neoforged.waifu.platform;

import java.io.IOException;
import java.io.InputStream;

public interface PlatformProjectFile {
    Object getProjectId();

    Object getId();

    PlatformProject getMod();

    String getHash();

    long getFileLength();

    InputStream download() throws IOException;

    ModPlatform getPlatform();

    String getUrl();
}
