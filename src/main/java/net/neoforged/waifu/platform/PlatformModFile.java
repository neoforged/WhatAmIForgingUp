package net.neoforged.waifu.platform;

import java.io.IOException;
import java.io.InputStream;

public interface PlatformModFile {
    Object getModId();

    Object getId();

    PlatformMod getMod();

    String getHash();

    long getFileLength();

    InputStream download() throws IOException;

    ModPlatform getPlatform();

    String getUrl();
}
