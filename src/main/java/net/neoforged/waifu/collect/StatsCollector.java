/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.collect;

import cpw.mods.jarhandling.SecureJar;
import net.neoforged.waifu.db.InheritanceDB;
import net.neoforged.waifu.db.ModIDsDB;
import net.neoforged.waifu.db.ProjectsDB;
import net.neoforged.waifu.db.RefsDB;
import net.neoforged.waifu.util.SemaphoreGroup;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("DuplicatedCode")
public class StatsCollector {
    public static final Logger LOGGER = LoggerFactory.getLogger(StatsCollector.class);
    private static final SemaphoreGroup SEMAPHORES = new SemaphoreGroup(Integer.parseInt(System.getProperty("indexing.max_threads", "50")));

    public static void collect(Map<ModPointer, SecureJar> jars, CollectorRule rule, ProjectsDB projects, InheritanceDB inheritance, RefsDB refs, ModIDsDB modIDsDB, Function<ModPointer, Collector> collectorFactory, ProgressMonitor monitor, boolean deleteOldData) throws InterruptedException, ExecutionException {
        jars.entrySet().removeIf(entry -> !rule.shouldCollect(entry.getKey().getModId()));

        projects.insert(0, 0); // Yay not null primary keys

        final Set<ModPointer> toKeep = new HashSet<>(jars.keySet());
        projects.useTransaction(transactional -> jars.keySet().removeIf(modPointer -> {
            if (modPointer.getProjectId() == 0) return false;
            final Integer oldFileId = transactional.getFileId(modPointer.getProjectId());
            // No need to re-calculate if we already did
            return Objects.equals(oldFileId, modPointer.getFileId());
        }));

        final var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .uncaughtExceptionHandler((t, e) -> LOGGER.error("Encountered exception collecting information: ", e))
                .name("stats-collector", 0).factory());
        monitor.setNumberOfMods(jars.size());

        if (!jars.isEmpty()) {
            final Semaphore semaphore = SEMAPHORES.acquireNew(jars.size());
            for (final var entry : jars.entrySet()) {
                executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        collect(entry.getKey().getModId(), entry.getValue(), rule, collectorFactory.apply(entry.getKey()), monitor);
                    } finally {
                        semaphore.release();
                    }
                    return null;
                });
            }
            try {
                executor.close();
            } finally {
                SEMAPHORES.release(semaphore);
            }
        }

        if (deleteOldData) {
            final Set<Integer> keptModsId = modIDsDB.inTransaction(transactional -> toKeep.stream()
                    .map(pointer -> transactional.get(pointer.getModId(), pointer.getProjectId()))
                    .collect(Collectors.toSet()));
            inheritance.delete(inheritance.getAllMods().stream()
                    .filter(Predicate.not(keptModsId::contains))
                    .toList());
            refs.delete(refs.getAllMods().stream()
                    .filter(Predicate.not(keptModsId::contains))
                    .toList());
        }
    }

    private static void collect(String modId, SecureJar jar, CollectorRule rule, Collector collector, ProgressMonitor monitor) throws IOException {
        monitor.startMod(modId);
        try (final Stream<Path> classes = Files.find(jar.getRootPath(), Integer.MAX_VALUE, (path, basicFileAttributes) -> path.getFileName().toString().endsWith(".class"))) {
            final ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {
                final ClassNode owner = new ClassNode();

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    owner.visit(version, access, name, signature, superName, interfaces);
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!rule.matches(descriptor)) return null;
                    final AnnotationNode node = new AnnotationNode(api, descriptor);
                    return new AnnotationVisitor(api, node) {
                        @Override
                        public void visitEnd() {
                            collector.acceptAnnotation(modId, owner, owner, node);
                        }
                    };
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!rule.matches(descriptor)) return null;
                            final AnnotationNode node = new AnnotationNode(api, descriptor);
                            return new AnnotationVisitor(api, node) {
                                @Override
                                public void visitEnd() {
                                    collector.acceptAnnotation(modId, owner, new FieldNode(access, name, descriptor, signature, value), node);
                                }
                            };
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    final MethodNode node = new MethodNode(access, name, descriptor, signature, exceptions);
                    final boolean isInit = name.equals("<init>");
                    owner.methods.add(node);
                    return new MethodVisitor(Opcodes.ASM9, node) {

                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!rule.matches(descriptor)) return null;
                            final AnnotationNode anNode = new AnnotationNode(api, descriptor);
                            return new AnnotationVisitor(api, anNode) {
                                @Override
                                public void visitEnd() {
                                    collector.acceptAnnotation(modId, owner, node, anNode);
                                }
                            };
                        }

                        @Override
                        public void visitEnd() {
                            boolean foundSuper = false;
                            for (final var insn : node.instructions) {
                                if (isInit && insn.getOpcode() == Opcodes.INVOKESPECIAL && !foundSuper && ((MethodInsnNode) insn).owner.equals(owner.superName)) {
                                    foundSuper = true;
                                    continue;
                                }

                                if (rule.matches(insn)) {
                                    collector.accept(modId, owner, node, insn);
                                }
                            }
                        }
                    };
                }

                @Override
                public void visitEnd() {
                    collector.accept(modId, owner);
                    owner.methods.clear();
                }
            };

            final Iterator<Path> cls = classes.iterator();
            while (cls.hasNext()) {
                final ClassReader reader = new ClassReader(Files.readAllBytes(cls.next()));
                reader.accept(visitor, 0);
            }
        }
        try {
            collector.commit();
            monitor.completedMod(modId, null);
        } catch (Exception exception) {
            monitor.completedMod(modId, exception);
        }
    }
}
