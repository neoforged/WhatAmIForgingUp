package net.neoforged.waifu.db;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface DataSanitizer {
    /**
     * Remove private or package members.
     */
    SanitizationRule REMOVE_PRIVATE_MEMBERS = (cls, ownedClasses) -> {
        cls.methods().entrySet().removeIf(m -> isPrivateMember(m.getValue().accessLevel()));
        cls.fields().entrySet().removeIf(f -> isPrivateMember(f.getValue().accessLevel()));

        return true;
    };

    SanitizationRule REMOVE_LAMBDAS = (cls, ownedClasses) -> {
        cls.methods().entrySet().removeIf(m -> m.getValue().name().startsWith("lambda$"));

        return true;
    };

    /**
     * Removes references to own members that are not a concern for gathering api usage.
     * This includes references to own static members or to own members which are private or package and therefore cannot be overriden in such a way that
     * we're interested in them.
     * References to members of own classes which have no parents are also removed, as those members are certainly not overriding anything.
     */
    SanitizationRule REMOVE_OWN_DIRECT_REFERENCES = (cls, ownedClasses) -> {
        cls.fieldRefs().entrySet().removeIf(e -> {
            var c = ownedClasses.get(e.getKey().owner());
            if (c != null) {
                var f = c.fields().get(e.getKey().name());
                return f != null && ((Modifier.isStatic(f.accessLevel()) || isPrivateMember(f.accessLevel())) || isRawClass(c));
            }
            return false;
        });
        cls.methodRefs().entrySet().removeIf(e -> {
            var c = ownedClasses.get(e.getKey().owner());
            if (c != null) {
                var m = c.methods().get(e.getKey().name() + e.getKey().desc());
                return m != null && ((Modifier.isStatic(m.accessLevel()) || isPrivateMember(m.accessLevel())) || isRawClass(c));
            }
            return false;
        });

        return true;
    };

    SanitizationRule REMOVE_ANONYMOUS_CLASSES = (cls, ownedClasses) -> {
        if (Character.isDigit(cls.name().charAt(cls.name().length() - 1))) {
            for (int i = cls.name().length() - 2; i >= 0; i--) {
                var ch = cls.name().charAt(i);
                if (!Character.isDigit(ch)) {
                    return ch != '$';
                }
            }
        }

        return true;
    };

    List<ClassData> sanitize(List<ClassData> classes);

    static DataSanitizer of(SanitizationRule... rules) {
        return classes -> {
            var byName = HashMap.<String, ClassData>newHashMap(classes.size());
            for (ClassData cls : classes) {
                byName.put(cls.name(), cls);
            }

            var newClasses = new ArrayList<ClassData>(classes.size());
            for (ClassData cls : classes) {
                ClassData newClass = cls.copy();
                boolean keep = true;
                for (SanitizationRule rule : rules) {
                    if (!rule.sanitize(newClass, byName)) {
                        keep = false;
                        break;
                    }
                }

                if (keep) {
                    newClasses.add(newClass);
                }
            }

            return newClasses;
        };
    }

    @FunctionalInterface
    interface SanitizationRule {
        boolean sanitize(ClassData cls, Map<String, ClassData> ownedClasses);
    }

    private static boolean isPrivateMember(int accessLevel) {
        // If a member is not public nor protected it's either private or package (which is basically private so we don't care about it)
        return !Modifier.isPublic(accessLevel) && !Modifier.isProtected(accessLevel);
    }

    private static boolean isRawClass(ClassData cls) {
        return cls.interfaces().length == 0 && (cls.superClass() == null || cls.superClass().equals("java/lang/Object"));
    }
}
