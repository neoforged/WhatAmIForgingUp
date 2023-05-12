package com.matyrobbrt.metabase.params;

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
