package net.neoforged.waifu.db;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ClassData(
        String name, @Nullable String superClass, String[] interfaces,

        Map<String, FieldInfo> fields,
        Map<String, MethodInfo> methods,

        Map<Reference, Integer> methodRefs,
        Map<Reference, Integer> fieldRefs
) {

    public record FieldInfo(String name, Type desc, int accessLevel) {}

    public record MethodInfo(
            String name, String desc, int accessLevel
    ) {}

    public record Reference(String owner, String name, String desc) {}

    public ClassData copy() {
        return new ClassData(
                name, superClass, interfaces,
                new HashMap<>(fields), new HashMap<>(methods),
                new HashMap<>(methodRefs), new HashMap<>(fieldRefs)
        );
    }
}
