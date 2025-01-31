package net.neoforged.waifu.util;

import java.util.concurrent.atomic.AtomicInteger;

public class Counter<T> {
    private final AtomicInteger counter;
    private final T[] arr;

    public Counter(AtomicInteger counter, T[] arr) {
        this.counter = counter;
        this.arr = arr;
    }

    public void add(T element) {
        counter.incrementAndGet();
        for (int i = arr.length - 1; i > 0; i--) {
            arr[i] = arr[i - 1];
        }
        arr[0] = element;
    }

    public T[] getElements() {
        return arr;
    }

    public int getAmount() {
        return counter.get();
    }
}
