/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.metabase.params;

import java.util.Map;

public interface QueryParameters {
    Map<String, Object> compile();
}
