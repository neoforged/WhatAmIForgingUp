/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.params;

import java.util.HashMap;
import java.util.Map;

public class DatabaseQueryParameters implements QueryParameters {
    private DatabaseInclusion inclusion;

    public DatabaseQueryParameters include(DatabaseInclusion inclusion) {
        this.inclusion = inclusion;
        return this;
    }

    @Override
    public Map<String, Object> compile() {
        final Map<String, Object> args = new HashMap<>();
        if (inclusion != null) {
            args.put("include", inclusion);
        }
        return args;
    }
}
