/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.matyrobbrt.stats.db;

import com.matyrobbrt.stats.collect.ModPointer;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public interface ProjectsDB extends Transactional<ProjectsDB> {
    @Nullable
    @SqlQuery("select fileId from projects where projectId = ?")
    Integer getFileId(int projectId);

    @SqlUpdate("insert into projects(projectId, fileId) values (:projectId, :fileId) on conflict(projectId) do update set fileId = :fileId")
    void insert(@Bind("projectId") int projectId, @Bind("fileId") int fileId);

    @SqlBatch("insert into projects(projectId, fileId) values (:projectId, :fileId) on conflict(projectId) do update set fileId = :fileId")
    void insertAll(@BindBean Iterable<ModPointer> mods);

    @SqlQuery("select fileId from projects")
    Set<Integer> getFileIDs();
}
