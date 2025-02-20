package net.neoforged.waifu.index;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Remapper {
    Remapper NOOP = new Remapper() {};

    default String remapClass(String className) {
        return className;
    }

    default String remapDesc(String desc) {
        return desc;
    }

    default String remapField(String className, String name, String desc) {
        return name;
    }

    default String remapFieldDesc(String className, String name, String desc) {
        return remapDesc(desc);
    }

    default String remapMethod(String className, String name, String desc) {
        return name;
    }

    default String remapMethodDesc(String className, String name, String desc) {
        return remapDesc(desc);
    }

    record DumbPrefixedId(
            @Nullable String classPrefix,
            Map<String, String> classNames,
            String methodPrefix,
            Map<String, String> methodNames,
            String fieldPrefix,
            Map<String, String> fieldNames
    ) implements Remapper {
        private static final Pattern DESC = Pattern.compile("L(?<cls>[^;]+);");

        @Override
        public String remapClass(String className) {
            if (classPrefix != null && className.startsWith(classPrefix)) {
                return classNames.getOrDefault(className, className);
            }
            return className;
        }

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

        @Override
        public String remapDesc(String desc) {
            if (classPrefix == null) return desc;

            Matcher matcher = DESC.matcher(desc);
            StringBuilder buf = new StringBuilder();
            while (matcher.find())
                matcher.appendReplacement(buf, Matcher.quoteReplacement("L" + remapClass(matcher.group("cls")) + ";"));
            matcher.appendTail(buf);
            return buf.toString();
        }

    }
}
