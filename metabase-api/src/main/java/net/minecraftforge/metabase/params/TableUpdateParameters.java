/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.metabase.params;

import com.google.gson.JsonObject;

public class TableUpdateParameters implements UpdateParameters {
    private String description;
    private String displayName;

    public TableUpdateParameters withDescription(String description) {
        this.description = description;
        return this;
    }

    public TableUpdateParameters withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public JsonObject compile() {
        final JsonObject object = new JsonObject();
        if (description != null) {
            object.addProperty("description", description);
        }
        if (displayName != null) {
            object.addProperty("display_name", displayName);
        }
        return object;
    }
}
