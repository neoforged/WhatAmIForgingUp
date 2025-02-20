package net.neoforged.waifu.index;

import java.util.Map;

public interface Remapper {
    Remapper NOOP = new Remapper() {};

    default String remapClass(String className) {
        return className;
    }

    default String remapField(String className, String name, String desc) {
        return name;
    }

    default String remapMethod(String className, String name, String desc) {
        return name;
    }

    default String remapMethodDesc(String className, String name, String desc) {
        return desc;
    }

    record DumbPrefixedId(
            String methodPrefix,
            Map<String, String> methodNames,
            String fieldPrefix,
            Map<String, String> fieldNames
    ) implements Remapper {
        @Override
        public String remapMethod(String className, String methodName, String methodDescriptor) {
            if (methodName.startsWith(methodPrefix)) {
                return methodNames.getOrDefault(methodName, methodName);
            }
            return methodName;
        }

        @Override
        public String remapField(String className, String fieldName, String fieldDescriptor) {
            if (fieldName.startsWith(fieldPrefix)) {
                return fieldNames.getOrDefault(fieldName, fieldName);
            }
            return fieldName;
        }
    }
}
