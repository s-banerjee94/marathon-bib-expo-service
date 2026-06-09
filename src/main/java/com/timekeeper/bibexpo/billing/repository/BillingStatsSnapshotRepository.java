package com.timekeeper.bibexpo.billing.repository;

import com.timekeeper.bibexpo.billing.model.entity.BillingStatsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read access to the precomputed billing-stats snapshot. The snapshot is written only by the
 * billing-stats Lambda; Spring reads the GLOBAL row here. There is intentionally no write method —
 * Spring never recomputes the stats.
 */
@Repository
public interface BillingStatsSnapshotRepository extends JpaRepository<BillingStatsSnapshot, Long> {

    /**
     * Fetch the snapshot for a scope.
     *
     * @param scope    the scope ({@link BillingStatsSnapshot#SCOPE_GLOBAL})
     * @param scopeKey {@link BillingStatsSnapshot#GLOBAL_SCOPE_SENTINEL} for the GLOBAL row
     * @return the snapshot if it has been computed at least once
     */
    Optional<BillingStatsSnapshot> findByScopeAndScopeKey(String scope, Long scopeKey);
}
