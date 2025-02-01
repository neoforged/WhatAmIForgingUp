package net.neoforged.waifu.util;

import java.util.List;

public interface ProgressMonitor<T> {
    void setExpected(List<T> elements);

    void unexpect(T element);

    void markAsIndexed(T element);

    void markAsStored(T element);

    void raiseError(T element, Throwable exception);
}
