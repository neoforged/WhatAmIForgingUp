package net.neoforged.waifu.db;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ClassData(
        String name, @Nullable String superClass, String[] interfaces,
        List<AnnotationInfo> annotations,

        Map<String, FieldInfo> fields,
        Map<String, MethodInfo> methods,

        Map<Reference, Integer> methodRefs,
        Map<Reference, Integer> fieldRefs
) {

    public record FieldInfo(String name, Type desc, int accessLevel, List<AnnotationInfo> annotations) {}

    public record MethodInfo(
            String name, String desc, int accessLevel, List<AnnotationInfo> annotations
    ) {}

    public record Reference(String owner, String name, String desc) {}

    public ClassData copy() {
        // TODO - we don't copy annotations - fix?
        return new ClassData(
                name, superClass, interfaces, annotations,
                new HashMap<>(fields), new HashMap<>(methods),
                new HashMap<>(methodRefs), new HashMap<>(fieldRefs)
        );
    }

    public record AnnotationInfo(
            Type type,
            Map<String, Object> members
    ) {}

    public record EnumValue(
            Type enumType,
            String value
    ) {
        @Override
        public String toString() {
            return enumType.getClassName() + ":" + value;
        }
    }
}
