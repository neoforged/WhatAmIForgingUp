/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.collect;

import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import org.jetbrains.annotations.Nullable;

public interface ProgressMonitor {
    void setNumberOfMods(int numberOfMods);
    void startMod(String id);
    void completedMod(String id, @Nullable Exception exception);

    void setDownloadTarget(int downloadTarget);
    void downloadEnded(File file);
}
