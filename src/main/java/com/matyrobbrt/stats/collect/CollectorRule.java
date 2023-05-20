/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.stats.collect;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public interface CollectorRule {

    boolean shouldCollect(String modId);

    boolean matches(AbstractInsnNode node);

    boolean matches(String annotationDesc);

    boolean oncePerMethod();

    boolean oncePerClass();

    static CollectorRule collectAll() {
        return new CollectorRule() {
            @Override
            public boolean shouldCollect(String modId) {
                return true;
            }

            @Override
            public boolean matches(AbstractInsnNode node) {
                return node instanceof MethodInsnNode || node instanceof FieldInsnNode ||
                        node instanceof LdcInsnNode ldc && ldc.cst.getClass() == org.objectweb.asm.Type.class;
            }

            @Override
            public boolean matches(String annotationDesc) {
                return !annotationDesc.endsWith("kotlin/Metadata;") && !annotationDesc.equals("Lscala/reflect/ScalaSignature;");
            }

            @Override
            public boolean oncePerMethod() {
                return true;
            }

            @Override
            public boolean oncePerClass() {
                return false;
            }
        };
    }
}
