/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.util;

import net.minecraftforge.srgutils.IMappingFile;

import java.util.HashMap;
import java.util.Map;

public interface Remapper {
    String remapMethod(String name);
    String remapField(String name);

    static Remapper fromMappings(IMappingFile file) {
        final Map<String, String> srgMethods = new HashMap<>();
        final Map<String, String> srgFields = new HashMap<>();

        file.getClasses().forEach(clazz -> {
            clazz.getMethods().forEach(method -> {
                if (method.getOriginal().startsWith("m_")) { // TODO - do we really need this?
                    srgMethods.put(method.getOriginal(), method.getMapped());
                }
            });
            clazz.getFields().forEach(fields -> {
                if (fields.getOriginal().startsWith("f_")) { // TODO - do we really need this?
                    srgFields.put(fields.getOriginal(), fields.getMapped());
                }
            });
        });
        return new Remapper() {
            @Override
            public String remapMethod(String name) {
                return srgMethods.getOrDefault(name, name);
            }

            @Override
            public String remapField(String name) {
                return srgFields.getOrDefault(name, name);
            }
        };
    }
}
