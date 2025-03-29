package net.neoforged.waifu.db.sql.search;

import java.util.ArrayList;
import java.util.List;

public class SqlArgumentContext {
    final List<Object> args = new ArrayList<>();

    public String insert(Object value) {
        args.add(value);
        return "#i" + (args.size() - 1);
    }
}
