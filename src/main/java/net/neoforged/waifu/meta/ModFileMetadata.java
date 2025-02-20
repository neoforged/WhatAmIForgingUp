package net.neoforged.waifu.meta;

import org.jetbrains.annotations.Nullable;

public record ModFileMetadata(
        @Nullable
        String license,
        String issueTrackerURL
) {
}
