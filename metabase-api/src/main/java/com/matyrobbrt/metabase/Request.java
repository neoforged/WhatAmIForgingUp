package com.matyrobbrt.metabase;

import com.google.gson.JsonElement;

import java.util.function.BiFunction;

public record Request<T>(String path, RequestMethod method, BiFunction<MetabaseClient, JsonElement, T> mapper, JsonElement body) {


}
