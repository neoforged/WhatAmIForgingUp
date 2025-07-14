package net.neoforged.waifu.db.sql.search;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record SqlOrder(OrderRule rule, boolean desc) {
    SqlOrder(Map<String, Object> map) {
        this(OrderRule.valueOf(((String) map.getOrDefault("by", "value")).toUpperCase(Locale.ROOT)), Objects.equals(map.get("direction"), "desc"));
    }

    public String createStatement(String column) {
        return rule.apply(column) + " " + (desc ? "desc" : "asc");
    }

    public enum OrderRule {
        VALUE {
            @Override
            public String apply(String in) {
                return in;
            }
        },
        LENGTH {
            @Override
            public String apply(String in) {
                return "length(" + in + ")";
            }
        };

        public abstract String apply(String in);
    }
}
