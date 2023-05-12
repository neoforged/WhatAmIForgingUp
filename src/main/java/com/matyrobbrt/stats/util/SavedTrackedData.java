package com.matyrobbrt.stats.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SavedTrackedData<T> {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .create();

    private final Object lock = new Object();

    private final Type type;
    private final Supplier<T> defaultValue;
    private final Path path;
    private T value;

    public SavedTrackedData(TypeToken<T> type, Supplier<T> defaultValue, Path path) {
        this.type = type.getType();
        this.defaultValue = defaultValue;
        this.path = path;

        this.refresh();
    }

    public <Z> Z withHandle(Function<T, Z> handler) {
        synchronized (lock) {
            refresh();
            final Z z = handler.apply(value);
            write();
            return z;
        }
    }

    public void useHandle(Consumer<T> handler) {
        synchronized (lock) {
            refresh();
            handler.accept(value);
            write();
        }
    }

    public T read() {
        refresh();
        return value;
    }

    private void refresh() {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                value = defaultValue.get();
                Files.writeString(path, GSON.toJson(value, type));
            } else {
                try (final var reader = Files.newBufferedReader(path)) {
                    value = GSON.fromJson(reader, type);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Could not refresh tracked value at " + path.toAbsolutePath(), ex);
        }
    }

    public void write() {
        try {
            Files.writeString(path, GSON.toJson(value, type));
        } catch (IOException e) {
            throw new RuntimeException("Could not write tracked value at " + path.toAbsolutePath(), e);
        }
    }
}
