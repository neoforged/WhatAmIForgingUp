/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.metabase.internal;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.matyrobbrt.metabase.MetabaseClient;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MetabaseTypeAdapterFactory(MetabaseClient client) implements TypeAdapterFactory {

    private static final Map<Class<?>, Object> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put(byte.class, (byte)0);
        DEFAULTS.put(int.class, 0);
        DEFAULTS.put(long.class, 0L);
        DEFAULTS.put(short.class, (short)0);
        DEFAULTS.put(double.class, 0D);
        DEFAULTS.put(float.class, 0F);
        DEFAULTS.put(char.class, '\0');
        DEFAULTS.put(boolean.class, false);
        DEFAULTS.put(List.class, List.of());
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) type.getRawType();
        if (!clazz.isRecord() && clazz.getAnnotation(MetabaseType.class) == null) {
            return null;
        }
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        var recordComponents = clazz.getRecordComponents();
        var typeMap = new HashMap<String,TypeToken<?>>();
        for (java.lang.reflect.RecordComponent recordComponent : recordComponents) {
            typeMap.put(getName(recordComponent), TypeToken.get(recordComponent.getGenericType()));
        }
        final boolean needsFull = typeMap.containsKey("_json");

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    return null;
                } else {
                    var argsMap = new HashMap<String,Object>();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (typeMap.containsKey(name)) {
                            argsMap.put(name, gson.getAdapter(typeMap.get(name)).read(reader));
                        } else {
                            reader.skipValue();
                        }
                    }
                    if (needsFull) {
                        argsMap.put("_json", gson.toJsonTree(argsMap));
                    }
                    argsMap.put("client", client);
                    reader.endObject();

                    var argTypes = new Class<?>[recordComponents.length];
                    var args = new Object[recordComponents.length];
                    for (int i = 0; i < recordComponents.length; i++) {
                        final var recComp = recordComponents[i];
                        argTypes[i] = recComp.getType();
                        String name = getName(recComp);
                        Object value = argsMap.get(name);
                        TypeToken<?> type = typeMap.get(name);
                        if (value == null && type != null) {
                            value = DEFAULTS.get(type.getRawType());
                        }
                        args[i] = value;
                    }
                    Constructor<T> constructor;
                    try {
                        constructor = clazz.getDeclaredConstructor(argTypes);
                        constructor.setAccessible(true);
                        return constructor.newInstance(args);
                    } catch (NoSuchMethodException | InstantiationException | SecurityException | IllegalAccessException | IllegalArgumentException |
                             InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    private String getName(RecordComponent component) {
        final SerializedName ann = component.getAnnotation(SerializedName.class);
        if (ann == null) {
            return component.getName();
        } else {
            return ann.value();
        }
    }
}