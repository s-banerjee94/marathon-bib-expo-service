package com.timekeeper.bibexpo.importer.repository;

import com.timekeeper.bibexpo.importer.model.entity.ImportRowError;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {

    /**
     * One page of an import's errors, keyset-paginated on the surrogate id. Rows are inserted in
     * row-number order, so ascending id is ascending row number and the cursor stays stable.
     */
    @Query("select e from ImportRowError e where e.importJob.importId = :importId and e.id > :afterId order by e.id asc")
    List<ImportRowError> findPage(@Param("importId") String importId,
                                  @Param("afterId") long afterId,
                                  Limit limit);

    /**
     * Clears every error belonging to an event's imports. Expressed as a subquery because a bulk
     * delete cannot join to the owning row.
     */
    @Modifying
    @Transactional
    @Query("delete from ImportRowError e where e.importJob.importId in "
            + "(select j.importId from ImportJob j where j.eventId = :eventId)")
    int deleteByEventId(@Param("eventId") Long eventId);
}