/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.collect;

import org.jetbrains.annotations.Nullable;

public interface ProgressMonitor {
    void setNumberOfMods(int numberOfMods);
    void startMod(String id);
    void completedMod(String id, @Nullable Exception exception);
}
