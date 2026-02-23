package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.EventStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.OrganizationStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.UserStatisticsResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Service for retrieving and refreshing role-scoped statistics snapshots.
 *
 * <p>Statistics are persisted as JSON snapshots in MySQL and auto-refreshed when stale.
 * Each section (users, organizations, events) is extracted from the same snapshot,
 * keeping DB writes minimal regardless of which endpoint is called.
 *
 * <p>Scope rules:
 * <ul>
 *   <li>ROOT / ADMIN → GLOBAL (system-wide data)</li>
 *   <li>ORGANIZER_ADMIN / ORGANIZER_USER → ORGANIZATION (own org only)</li>
 * </ul>
 */
public interface AppStatisticsService {

    /**
     * Returns role-scoped user statistics. Auto-refreshes when stale.
     *
     * @param currentUser the authenticated user making the request
     * @return user statistics for the appropriate scope
     */
    UserStatisticsResponse getUserStatistics(User currentUser);

    /**
     * Forces immediate recomputation of the snapshot and returns the user statistics section.
     *
     * @param currentUser the authenticated user triggering the refresh
     * @return freshly computed user statistics
     */
    UserStatisticsResponse refreshUserStatistics(User currentUser);

    /**
     * Returns global organization statistics. Accessible by ROOT and ADMIN only.
     * Auto-refreshes when stale.
     *
     * @param currentUser the authenticated user making the request
     * @return global organization statistics
     */
    OrganizationStatisticsResponse getOrganizationStatistics(User currentUser);

    /**
     * Forces immediate recomputation of the snapshot and returns the organization statistics section.
     *
     * @param currentUser the authenticated user triggering the refresh
     * @return freshly computed organization statistics
     */
    OrganizationStatisticsResponse refreshOrganizationStatistics(User currentUser);

    /**
     * Returns role-scoped event statistics. Auto-refreshes when stale.
     *
     * @param currentUser the authenticated user making the request
     * @return event statistics for the appropriate scope
     */
    EventStatisticsResponse getEventStatistics(User currentUser);

    /**
     * Forces immediate recomputation of the snapshot and returns the event statistics section.
     *
     * @param currentUser the authenticated user triggering the refresh
     * @return freshly computed event statistics
     */
    EventStatisticsResponse refreshEventStatistics(User currentUser);
}
