/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase.params;

import java.util.Map;

public interface QueryParameters {
    Map<String, Object> compile();
}
