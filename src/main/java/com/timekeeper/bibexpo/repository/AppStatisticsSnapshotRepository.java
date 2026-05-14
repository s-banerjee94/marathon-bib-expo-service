package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.AppStatisticsSnapshot;
import com.timekeeper.bibexpo.model.enums.StatisticsScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AppStatisticsSnapshotRepository extends JpaRepository<AppStatisticsSnapshot, Long> {

    /**
     * Fetches a snapshot by scope and organization ID.
     * For GLOBAL scope, pass {@link AppStatisticsSnapshot#GLOBAL_SCOPE_SENTINEL} as organizationId.
     *
     * @param scope          the statistics scope
     * @param organizationId real org ID for ORGANIZATION scope; 0 (sentinel) for GLOBAL scope
     * @return the snapshot if it exists
     */
    Optional<AppStatisticsSnapshot> findByScopeAndOrganizationId(StatisticsScope scope, Long organizationId);

    /**
     * Atomically inserts or updates a snapshot, preventing duplicate-key errors
     * when multiple threads refresh the same scope/org concurrently.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_statistics_snapshot (scope, organization_id, snapshot_data, refreshed_at, created_at)
            VALUES (:scope, :organizationId, :snapshotData, :refreshedAt, :refreshedAt)
            ON DUPLICATE KEY UPDATE snapshot_data = :snapshotData, refreshed_at = :refreshedAt
            """, nativeQuery = true)
    void upsert(@Param("scope") String scope,
                @Param("organizationId") Long organizationId,
                @Param("snapshotData") String snapshotData,
                @Param("refreshedAt") Instant refreshedAt);
}
