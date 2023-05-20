/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.stats.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntConsumer;

public class DynamicSemaphore extends Semaphore {
    static final VarHandle SYNC_FIELD;
    static final MethodHandle compareAndSetState;

    static {
        try {
            final Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            implLookup.setAccessible(true);
            final MethodHandles.Lookup lookup = (MethodHandles.Lookup) implLookup.get(null);
            final Class<?> syncClass = lookup.findClass("java.util.concurrent.Semaphore$Sync");
            SYNC_FIELD = lookup.findVarHandle(Semaphore.class, "sync", syncClass);

            compareAndSetState = lookup.findVirtual(AbstractQueuedSynchronizer.class, "compareAndSetState", MethodType.methodType(boolean.class, int.class, int.class));
        } catch (Exception exception) {
            throw new RuntimeException("BARF!", exception);
        }
    }

    private final IntConsumer setter;
    public DynamicSemaphore(int permits) {
        super(permits);
        final Object sync = SYNC_FIELD.get(this);
        setter = value -> {
            for (;;) {
                try {
                    if ((boolean)compareAndSetState.invoke(sync, availablePermits(), value)) {
                        return;
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public void setPermits(int permits) {
        setter.accept(permits);
    }
}