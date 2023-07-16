/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.collect;

import com.google.common.base.Stopwatch;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DiscordProgressMonitor implements ProgressMonitor {
    private final Message loggingMessage;
    private final String initialMessage;
    private final BiConsumer<String, Exception> exceptionallyConsumer;

    private final AtomicInteger numberOfMods;
    private final AtomicInteger completed;
    private final List<String> currentMods;
    private final Map<String, Exception> exceptionally;

    private final AtomicInteger toDownload = new AtomicInteger(-1);
    private final AtomicInteger downloaded = new AtomicInteger(-1);

    public DiscordProgressMonitor(Message loggingMessage, BiConsumer<String, Exception> exceptionallyConsumer) {
        this.loggingMessage = loggingMessage;
        this.initialMessage = loggingMessage.getContentRaw();
        this.exceptionallyConsumer = exceptionallyConsumer;
        this.numberOfMods = new AtomicInteger(-1);
        this.completed = new AtomicInteger(0);
        this.currentMods = new ArrayList<>();
        this.exceptionally = new HashMap<>();
        setupMonitor();
    }

    public void markCollection(int fileAmount) {
        loggingMessage.editMessage(initialMessage + ":\n" + (fileAmount == -1 ? "Collecting mods." : "Collecting mods from " + fileAmount + " files.")).complete();
    }

    private void setupMonitor() {
        final Stopwatch start = Stopwatch.createStarted();
        ForkJoinPool.commonPool().submit(() -> {
            final long monitoringInterval = Duration.ofSeconds(2).toMillis();
            final AtomicLong last = new AtomicLong(System.currentTimeMillis());

            while (true) {
                if (System.currentTimeMillis() - last.get() < monitoringInterval) continue;
                last.set(System.currentTimeMillis());

                final int num = numberOfMods.get();
                final int com = completed.get();
                final StringBuilder content = new StringBuilder()
                        .append(initialMessage).append(":\n");

                boolean breakOut = false;
                if (num == -1) {
                    final int toDown = toDownload.get();
                    if (toDown == -1 || toDown == downloaded.get())
                        continue; // We haven't started yet

                    if (toDown == 0) {
                        content.append("Found no mods to download!");
                    } else {
                        content.append("Downloading mods... Currently downloaded ").append(downloaded.get()).append("/").append(toDown).append(".");
                    }
                } else {
                    if (num == com) {
                        content.append("Completed scanning of ").append(num).append(" mods in ").append(start.stop().elapsed(TimeUnit.SECONDS)).append(" seconds!");
                        breakOut = true;
                    } else {
                        synchronized (currentMods) {
                            if (currentMods.isEmpty()) {
                                content.append("Currently idling...");
                            } else {
                                content.append(IntStream.range(0, Math.max(currentMods.size(), 10))
                                        .mapToObj(i -> "- " + currentMods.get(i) + " (" + (com + i + 1) + "/" + num + ")")
                                        .collect(Collectors.joining("\n")));
                                if (currentMods.size() > 10) {
                                    content.append("- ... and ").append(currentMods.size() - 10).append(" more");
                                }
                            }
                        }
                    }
                }

                synchronized (exceptionally) {
                    if (!exceptionally.isEmpty()) {
                        content.append("\n❌ Completed exceptionally:")
                                .append(String.join(", ", exceptionally.keySet()));
                    }
                }

                loggingMessage.editMessage(content.toString()).complete();
                if (breakOut) {
                    break;
                }
            }
        });
    }

    @Override
    public void setNumberOfMods(int numberOfMods) {
        this.numberOfMods.set(numberOfMods);
    }

    @Override
    public void startMod(String id) {
        synchronized (currentMods) {
            currentMods.add(id);
        }
    }

    @Override
    public void completedMod(String id, @Nullable Exception exception) {
        synchronized (currentMods) {
            currentMods.remove(id);
            completed.incrementAndGet();

            if (exception != null) {
                synchronized (exceptionally) {
                    exceptionally.put(id, exception);
                    exceptionallyConsumer.accept(id, exception);
                }
            }
        }
    }

    @Override
    public void downloadEnded(File file) {
        downloaded.incrementAndGet();
    }

    @Override
    public void setDownloadTarget(int downloadTarget) {
        toDownload.set(downloadTarget);
    }
}
