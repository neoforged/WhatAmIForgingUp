package com.matyrobbrt.metabase.params;

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
