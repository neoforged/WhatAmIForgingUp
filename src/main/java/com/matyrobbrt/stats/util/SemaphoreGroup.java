package com.matyrobbrt.stats.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class SemaphoreGroup {
    private final Map<DynamicSemaphore, Integer> semaphores = new ConcurrentHashMap<>();
    private final int limit;

    public SemaphoreGroup(int limit) {
        this.limit = limit;
    }

    public Semaphore acquireNew(int amount) {
        final DynamicSemaphore sem = new DynamicSemaphore(0);
        semaphores.put(sem, amount);
        redistribute();
        return sem;
    }

    public void release(Semaphore semaphore) {
        semaphores.remove(semaphore);
        redistribute();
    }

    private void redistribute() {
        if (semaphores.isEmpty()) return;
        final int baseline = limit / semaphores.size();
        final int extra = limit % semaphores.size();
        semaphores.keySet().forEach(sem -> sem.setPermits(baseline));
        final var sorted = semaphores.entrySet().stream().sorted(Map.Entry.<DynamicSemaphore, Integer>comparingByValue().reversed()).toList();
        for (int i = 0; i < extra; i++) {
            final var sem = sorted.get(i % sorted.size()).getKey();
            sem.setPermits(sem.availablePermits() + 1);
        }
    }
}
