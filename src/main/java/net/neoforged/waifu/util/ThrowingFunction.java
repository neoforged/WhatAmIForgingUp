package net.neoforged.waifu.util;

public interface ThrowingFunction<T, R, E extends Throwable> {
    R apply(T input) throws E;
}
