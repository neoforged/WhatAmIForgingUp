/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.util;

import java.util.Comparator;
import java.util.stream.Stream;

public enum ByteConversion {
    B("B", 1),
    KB("KB", 1000),
    MB("MB", 1000000),
    GB("GB", 1000 * 1000000);

    private final String unit;
    private final long bytes;

    ByteConversion(String unit, long bytes) {
        this.unit = unit;
        this.bytes = bytes;
    }

    public long fromBytes(long bytes) {
        return bytes / this.bytes;
    }

    public double fromBytes(double bytes) {
        return bytes / this.bytes;
    }

    public String fromBytesHumanReadable(long bytes) {
        return String.format("%.2f",(double)bytes / this.bytes) + unit;
    }

    public static String formatBest(long bytes) {
        final ByteConversion conversion = Stream.of(values())
                .sorted(Comparator.<ByteConversion, Long>comparing(c -> c.bytes).reversed())
                .filter(c -> bytes / c.bytes > 0).findFirst()
                .orElse(ByteConversion.B);
        return conversion.fromBytesHumanReadable(bytes);
    }

    @Override
    public String toString() {
        return unit;
    }
}
