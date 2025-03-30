package net.neoforged.waifu.db.sql.search;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import java.util.List;
import java.util.Map;

public class EmptySelectionSet implements DataFetchingFieldSelectionSet {
    public static final EmptySelectionSet INSTANCE = new EmptySelectionSet();

    @Override
    public boolean contains(String fieldGlobPattern) {
        return false;
    }

    @Override
    public boolean containsAnyOf(String fieldGlobPattern, String... fieldGlobPatterns) {
        return false;
    }

    @Override
    public boolean containsAllOf(String fieldGlobPattern, String... fieldGlobPatterns) {
        return false;
    }

    @Override
    public List<SelectedField> getFields() {
        return List.of();
    }

    @Override
    public List<SelectedField> getImmediateFields() {
        return List.of();
    }

    @Override
    public List<SelectedField> getFields(String fieldGlobPattern, String... fieldGlobPatterns) {
        return List.of();
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
        return Map.of();
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String fieldGlobPattern, String... fieldGlobPatterns) {
        return Map.of();
    }
}
