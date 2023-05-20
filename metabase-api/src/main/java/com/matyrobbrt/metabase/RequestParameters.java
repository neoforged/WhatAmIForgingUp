/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.metabase;

import com.matyrobbrt.metabase.params.QueryParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RequestParameters {
    public static final Pattern PATH_PATTERN = Pattern.compile(":([a-z0-9]+)");

    private final List<Object> indexedValues = new ArrayList<>();
    private final Map<String, Object> values = new HashMap<>();

    private RequestParameters(Object[] values) {
        int i = 0;
        for (; i < values.length; i++) {
            final Object o = values[i];
            if (o instanceof String) break;
            indexedValues.add(o);
        }
        for (; i < values.length; i += 2) {
            final String key = values[i].toString();
            if (i + 1 >= values.length) {
                throw new IllegalArgumentException("RequestParameters with odd number of named arguments!");
            }
            this.values.put(key, values[i + 1]);
        }
    }

    public static RequestParameters of(Object... values) {
        return new RequestParameters(values);
    }

    public String compilePath(String path) {
        final String newPath = PATH_PATTERN.matcher(path).replaceAll(result -> {
            final String group = result.group(1);
            try {
                final int num = Integer.parseInt(group);
                if (num >= indexedValues.size()) {
                    return get(group).toString();
                }
                return getIndex(num).toString();
            } catch (Exception ex) {
                return get(group).toString();
            }
        });
        return indexedValues.stream().filter(it -> it instanceof QueryParameters)
                .findFirst().map(it -> newPath + "?" + ((QueryParameters) it).compile().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&")))
                .orElse(newPath);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Object getIndex(int index) {
        return indexedValues.get(index);
    }
}
