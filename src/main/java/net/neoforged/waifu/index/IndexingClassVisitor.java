package net.neoforged.waifu.index;

import net.neoforged.waifu.db.ClassData;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IndexingClassVisitor extends ClassVisitor {
    private final List<ClassData> classList;
    private final boolean includeReferences;
    private final boolean includeAnnotations;

    private ClassData current;

    public IndexingClassVisitor(List<ClassData> classList, boolean includeReferences, boolean includeAnnotations) {
        super(Opcodes.ASM9);
        this.classList = classList;
        this.includeReferences = includeReferences;
        this.includeAnnotations = includeAnnotations;
    }

    public static List<ClassData> collect(Path directory, boolean includeReferences, boolean includeAnnotations) throws IOException {
        List<ClassData> classes = new ArrayList<>();
        var indexer = new IndexingClassVisitor(classes, includeReferences, includeAnnotations);

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                var fileName = file.getFileName().toString();
                if (fileName.endsWith(".class")) {
                    try (var is = Files.newInputStream(file)) {
                        new ClassReader(is).accept(indexer, includeReferences ? ClassReader.SKIP_DEBUG : (ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });
        return classes;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (!name.endsWith("package-info") && !name.endsWith("module-info")) {
            current = new ClassData(
                    name, superName, interfaces, new ArrayList<>(0), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(1 << 3)
            );
            classList.add(current);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var method = new ClassData.MethodInfo(name, descriptor, access, new ArrayList<>(0));
        current.methods().put(name + descriptor, method);
        return includeReferences || includeAnnotations ? new MethodVisitor(Opcodes.ASM9) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (includeReferences) {
                    current.methodRefs().merge(new ClassData.Reference(owner, name, descriptor), 1, Integer::sum);
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (includeReferences) {
                    current.fieldRefs().merge(new ClassData.Reference(owner, name, Type.getType(descriptor).getInternalName()), 1, Integer::sum);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (includeAnnotations && visible && shouldVisitAnnotations(descriptor)) {
                    var info = new ClassData.AnnotationInfo(Type.getType(descriptor), new HashMap<>(2));
                    method.annotations().add(info);
                    return visitor(info);
                }
                return null;
            }
        } : null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (current != null && includeAnnotations && visible && shouldVisitAnnotations(descriptor)) {
            var info = new ClassData.AnnotationInfo(Type.getType(descriptor), new HashMap<>(2));
            current.annotations().add(info);
            return visitor(info);
        }
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        var field = new ClassData.FieldInfo(name, Type.getType(descriptor), access, new ArrayList<>(0));
        current.fields().put(name, field);
        return includeAnnotations ? new FieldVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (visible && shouldVisitAnnotations(descriptor)) {
                    var info = new ClassData.AnnotationInfo(Type.getType(descriptor), new HashMap<>(2));
                    field.annotations().add(info);
                    return visitor(info);
                }
                return null;
            }
        } : null;
    }

    private AnnotationVisitor visitor(ClassData.AnnotationInfo annotationInfo) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                annotationInfo.members().put(name, value);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                if (shouldVisitAnnotations(descriptor)) {
                    var newAn = new ClassData.AnnotationInfo(Type.getType(descriptor), new HashMap<>(2));
                    annotationInfo.members().put(name, newAn);
                    return visitor(newAn);
                }
                return null;
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                annotationInfo.members().put(name, new ClassData.EnumValue(Type.getType(descriptor), value));
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                var lst = new ArrayList<>(2);
                annotationInfo.members().put(name, lst);
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        lst.add(value);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        if (shouldVisitAnnotations(descriptor)) {
                            var newAn = new ClassData.AnnotationInfo(Type.getType(descriptor), new HashMap<>(2));
                            lst.add(newAn);
                            return visitor(newAn);
                        }
                        return null;
                    }

                    @Override
                    public void visitEnum(String name, String descriptor, String value) {
                        lst.add(new ClassData.EnumValue(Type.getType(descriptor), value));
                    }
                };
            }
        };
    }

    private boolean shouldVisitAnnotations(String ann) {
        // scala/reflect/ScalaSignature
        return !ann.endsWith("kotlin/Metadata;") && !ann.contains("scala/reflect/");
    }
}
