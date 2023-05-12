package com.matyrobbrt.stats.db;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface RefsDB extends Transactional<RefsDB> {

//    @BatchChunkSize(5000)
    @SqlBatch("insert into refs(modId, amount, owner, member, type) values (:modId, :amount, :owner, :member, :type)")
    void insert(@Bind("modId") int modId, @BindBean Iterable<Reference> references, @Bind("amount") Iterable<AtomicInteger> amount);

    @SqlQuery("select distinct modId from refs")
    List<Integer> getAllMods();

    @SqlUpdate("delete from refs where modId = :modId;")
    void delete(@Bind("modId") int modId);

    @SqlBatch("delete from refs where modId = :modId;")
    void delete(@Bind("modId") Iterable<Integer> ids);
}
