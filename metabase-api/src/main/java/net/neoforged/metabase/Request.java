/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.metabase;

import com.google.gson.JsonElement;

import java.util.function.BiFunction;

public record Request<T>(String path, RequestMethod method, BiFunction<MetabaseClient, JsonElement, T> mapper, JsonElement body) {


}
