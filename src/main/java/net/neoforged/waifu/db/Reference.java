/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.db;

import org.jdbi.v3.core.enums.EnumByOrdinal;

public record Reference(
        String owner,
        String member,
        Type type
) {
    public String getOwner() {
        return owner;
    }

    public String getMember() {
        return member;
    }

    @EnumByOrdinal
    public Type getType() {
        return type;
    }
}
