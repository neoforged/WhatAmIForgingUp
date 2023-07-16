/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.params;

public enum DatabaseInclusion {
    TABLES {
        @Override
        public String toString() {
            return "tables";
        }
    },
    TABLES_AND_FIELDS {
        @Override
        public String toString() {
            return "tables.fields";
        }
    }
}
