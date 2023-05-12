package com.matyrobbrt.stats.collect;

import org.jetbrains.annotations.Nullable;

public interface ProgressMonitor {
    void setNumberOfMods(int numberOfMods);
    void startMod(String id);
    void completedMod(String id, @Nullable Exception exception);
}
