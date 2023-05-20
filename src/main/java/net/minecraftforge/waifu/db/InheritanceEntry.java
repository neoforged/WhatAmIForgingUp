/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.db;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record InheritanceEntry(
        String clazz, @Nullable String superClass,
        List<String> interfaces, String[] methods
) {
    public String getClazz() {
        return clazz;
    }

    @Nullable
    public String getSuperClass() {
        return superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public String[] getMethods() {
        return methods;
    }
}
