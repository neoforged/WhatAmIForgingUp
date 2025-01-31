package net.neoforged.waifu.util;

public interface ThrowingConsumer<A, E extends Throwable> {
    void accept(A a) throws E;
}
