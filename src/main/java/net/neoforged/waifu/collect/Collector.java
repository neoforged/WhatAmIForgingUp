/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.collect;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface Collector {

    default void accept(String modId, ClassNode clazz) {}

    void accept(String modId, ClassNode owner, MethodNode methodNode, AbstractInsnNode node);

    default void acceptAnnotation(String modId, ClassNode clazz, Object owner, AnnotationNode node) {}

    default void commit() {}
}
