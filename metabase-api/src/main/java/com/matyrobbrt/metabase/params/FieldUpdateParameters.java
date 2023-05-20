/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.metabase.params;

import com.google.gson.JsonObject;
import com.matyrobbrt.metabase.types.Field;

public class FieldUpdateParameters implements UpdateParameters {
    private final Field source;
    private Field target;
    private FieldValues fieldValues;

    public FieldUpdateParameters(Field source) {
        this.source = source;
    }

    public FieldUpdateParameters setTarget(Field target) {
        this.target = target;
        return this;
    }

    public FieldUpdateParameters withFieldValues(FieldValues values) {
        this.fieldValues = values;
        return this;
    }

    @Override
    public JsonObject compile() {
        final JsonObject update = source._json().deepCopy();
        if (target != null) {
            update.add("target", target._json().deepCopy());
        }
        if (fieldValues != null) {
            update.addProperty("has_field_values", fieldValues.toString());
        }
        return update;
    }
}
