/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.stats.db;

import com.matyrobbrt.stats.collect.ModPointer;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.util.Map;

public interface ModIDsDB extends Transactional<ModIDsDB> {
    @Nullable
    @SqlQuery("select id from modids where modId = ? and projectId = ?")
    Integer getId(String modId, int projectId);

    default int get(String modId, int projectId) {
        final Integer i = getId(modId, projectId);
        if (i != null) {
            return i;
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
