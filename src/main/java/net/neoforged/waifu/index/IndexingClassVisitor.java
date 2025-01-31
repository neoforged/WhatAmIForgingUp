package net.neoforged.waifu.index;

import net.neoforged.waifu.db.ClassData;
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

    private ClassData current;

    public IndexingClassVisitor(List<ClassData> classList, boolean includeReferences) {
        super(Opcodes.ASM9);
        this.classList = classList;
        this.includeReferences = includeReferences;
    }

    public static List<ClassData> collect(Path directory, boolean includeReferences) throws IOException {
        List<ClassData> classes = new ArrayList<>();
        var indexer = new IndexingClassVisitor(classes, includeReferences);

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                var fileName = file.getFileName().toString();
                if (fileName.endsWith(".class")) {
                    try (var is = Files.newInputStream(file)) {
                        new ClassReader(is).accept(indexer, ClassReader.SKIP_DEBUG);
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
                    name, superName, interfaces, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(1 << 3)
            );
            classList.add(current);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var method = new ClassData.MethodInfo(name, descriptor, access);
        current.methods().put(name + descriptor, method);
        return includeReferences ? new MethodVisitor(Opcodes.ASM9) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                current.methodRefs().merge(new ClassData.Reference(owner, name, descriptor), 1, Integer::sum);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                current.fieldRefs().merge(new ClassData.Reference(owner, name, Type.getType(descriptor).getInternalName()), 1, Integer::sum);
            }
        } : null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        current.fields().put(name, new ClassData.FieldInfo(name, Type.getType(descriptor), access));
        return super.visitField(access, name, descriptor, signature, value);
    }
}
