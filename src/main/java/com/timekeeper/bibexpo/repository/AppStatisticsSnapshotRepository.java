package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.AppStatisticsSnapshot;
import com.timekeeper.bibexpo.model.enums.StatisticsScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
