/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.metabase.params;

public enum FieldValues {
    NONE {
        @Override
        public String toString() {
            return "none";
        }
    },

    SEARCH {
        @Override
        public String toString() {
            return "search";
        }
    },

    LIST {
        @Override
        public String toString() {
            return "list";
        }
    }
}
