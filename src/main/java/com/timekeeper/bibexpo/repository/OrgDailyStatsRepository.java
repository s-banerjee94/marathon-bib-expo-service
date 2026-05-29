package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.OrgDailyStats;
import com.timekeeper.bibexpo.model.entity.OrgDailyStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrgDailyStatsRepository extends JpaRepository<OrgDailyStats, OrgDailyStatsId> {

    /**
     * Loads all snapshot rows for an org within a date range, ordered by date ascending.
     * Used by TrendsService to build the trend series.
     */
    @Query("SELECT s FROM OrgDailyStats s WHERE s.organizationId = :orgId AND s.snapshotDate BETWEEN :from AND :to ORDER BY s.snapshotDate ASC")
    List<OrgDailyStats> findByOrgAndDateRange(@Param("orgId") Long orgId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * Idempotent upsert — inserts a new row or overwrites an existing one for the same (organization_id, snapshot_date).
     */
    @Modifying
    @Query(value = """
            INSERT INTO org_daily_stats
              (organization_id, snapshot_date, total_events, active_events, total_users, distinct_cities, computed_at)
            VALUES (:orgId, :date, :events, :active, :users, :cities, NOW())
            ON DUPLICATE KEY UPDATE
              total_events    = :events,
              active_events   = :active,
              total_users     = :users,
              distinct_cities = :cities,
              computed_at     = NOW()
            """, nativeQuery = true)
    void upsert(@Param("orgId") Long orgId,
                @Param("date") LocalDate date,
                @Param("events") int events,
                @Param("active") int active,
                @Param("users") int users,
                @Param("cities") int cities);
}
