/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.params;

import com.google.gson.JsonObject;

public class DatabaseUpdateParameters implements UpdateParameters {
    private String name;
    private String description;

    public DatabaseUpdateParameters withName(String name) {
        this.name = name;
        return this;
    }

    public DatabaseUpdateParameters withDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public JsonObject compile() {
        final JsonObject object = new JsonObject();
        if (name != null) {
            object.addProperty("name", name);
        }
        if (description != null) {
            object.addProperty("description", description);
        }
        return object;
    }
}
