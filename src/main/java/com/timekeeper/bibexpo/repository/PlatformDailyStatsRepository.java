package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.PlatformDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlatformDailyStatsRepository extends JpaRepository<PlatformDailyStats, LocalDate> {

    /**
     * Loads all snapshot rows within a date range, ordered by date ascending.
     * Used by the trends builder to assemble the platform growth series.
     *
     * @param from inclusive lower bound
     * @param to   inclusive upper bound
     * @return matching rows ordered by snapshotDate ascending
     */
    @Query("SELECT s FROM PlatformDailyStats s WHERE s.snapshotDate BETWEEN :from AND :to ORDER BY s.snapshotDate ASC")
    List<PlatformDailyStats> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * The snapshot dates that already exist within a range — lets the backfill skip days
     * it has already seeded (and never overwrite an accurate forward snapshot).
     *
     * @param from inclusive lower bound
     * @param to   inclusive upper bound
     * @return existing snapshot dates in range
     */
    @Query("SELECT s.snapshotDate FROM PlatformDailyStats s WHERE s.snapshotDate BETWEEN :from AND :to")
    List<LocalDate> findExistingDates(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Idempotent upsert — inserts a new row or overwrites the existing one for the same date.
     */
    @Modifying
    @Query(value = """
            INSERT INTO platform_daily_stats
              (snapshot_date, organizations, total_events, active_events, total_users, distinct_cities, computed_at)
            VALUES (:date, :orgs, :events, :active, :users, :cities, NOW())
            ON DUPLICATE KEY UPDATE
              organizations   = :orgs,
              total_events    = :events,
              active_events   = :active,
              total_users     = :users,
              distinct_cities = :cities,
              computed_at     = NOW()
            """, nativeQuery = true)
    void upsert(@Param("date") LocalDate date,
                @Param("orgs") int orgs,
                @Param("events") int events,
                @Param("active") int active,
                @Param("users") int users,
                @Param("cities") int cities);
}
