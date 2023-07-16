/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public class CountingInputStream extends InputStream {
    private final InputStream is;
    private final AtomicLong counter;

    public CountingInputStream(InputStream is) {
        this(is, new AtomicLong());
    }

    public CountingInputStream(InputStream is, AtomicLong counter) {
        this.is = is;
        this.counter = counter;
    }

    @Override
    public int read() throws IOException {
        final int r = is.read();
        if (r != -1) counter.incrementAndGet();
        return r;
    }

    @Override
    public void close() throws IOException {
        is.close();
    }

    public AtomicLong getCounter() {
        return counter;
    }
}
