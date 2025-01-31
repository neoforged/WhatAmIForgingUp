package net.neoforged.waifu.util;

import java.util.Iterator;
import java.util.function.Function;

public record MappingIterator<T, R>(Iterator<T> itr, Function<T, R> function) implements Iterator<R> {
    @Override
    public boolean hasNext() {
        return itr.hasNext();
    }

    @Override
    public R next() {
        return function.apply(itr.next());
    }
}
