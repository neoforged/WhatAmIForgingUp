/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu.collect;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public final class ModPointer {
    private final String modId;
    private final int projectId;
    private final int fileId;

    public ModPointer(String modId, int projectId, int fileId) {
        this.modId = modId;
        this.projectId = projectId;
        this.fileId = fileId;
    }

    public ModPointer(String modId) {
        this.modId = modId;
        this.projectId = 0;
        this.fileId = 0;
    }

    private ModPointer(String modId, int projectId) {
        this.modId = modId;
        this.projectId = projectId;
        this.fileId = 0;
    }

    public String getModId() {
        return modId;
    }

    public int getProjectId() {
        return projectId;
    }

    public Integer getFileId() {
        return fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModPointer that = (ModPointer) o;
        return Objects.equals(modId, that.modId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modId, projectId);
    }

    public static final class Mapper implements RowMapper<ModPointer> {

        @Override
        public ModPointer map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ModPointer(rs.getString("modId"), rs.getInt("projectId"));
        }
    }

}
