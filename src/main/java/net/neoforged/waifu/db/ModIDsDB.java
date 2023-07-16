/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.db;

import net.neoforged.waifu.collect.ModPointer;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public interface ModIDsDB extends Transactional<ModIDsDB> {
    @Nullable
    @SqlQuery("select id from modids where modId = ? and projectId = ?")
    Integer getId(String modId, int projectId);

    @SqlQuery("select id from modids where modId = ?")
    List<Integer> getIds(String modId);

    default int get(String modId, int projectId) {
        Integer i = getId(modId, projectId);
        if (i != null) { // Two CF mods with the same ID shouldn't match
            return i;
        } else if (projectId == 0) { // But a JiJ'd mod should match one from CF
            final var ids = getIds(modId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        } else {
            if ((i = getId(modId, 0)) != null) { // And a mod from CF should match a JiJ'd one
                return i;
            }
        }
        return getHandle().createUpdate("insert into modids(modId, projectId) values (?, ?) returning id")
                .bind(0, modId)
                .bind(1, projectId)
                .execute((statementSupplier, ctx) -> {
                    final ResultSet resultSet = statementSupplier.get().getResultSet();
                    resultSet.next();
                    return resultSet.getInt("id");
                });
    }

    @KeyColumn("id")
    @UseRowMapper(ModPointer.Mapper.class)
    @SqlQuery("select * from modids")
    Map<Integer, ModPointer> getAll();
}
