/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.db;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;

public interface InheritanceDB extends Transactional<InheritanceDB> {
    @SqlBatch("insert into inheritance(modId, class, super, interfaces, methods) values (:modId, :clazz, :superClass, :interfaces, :methods)")
    void insert(@Bind("modId") int modId, @BindBean Iterable<InheritanceEntry> entries);

    @SqlQuery("select distinct modId from inheritance")
    List<Integer> getAllMods();

    @SqlUpdate("delete from inheritance where modId = :modId;")
    void delete(@Bind("modId") int modId);

    @SqlBatch("delete from inheritance where modId = :modId;")
    void delete(@Bind("modId") Iterable<Integer> ids);
}
