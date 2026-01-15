package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingBibListResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingGoodiesListResponse;
import com.timekeeper.bibexpo.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

public interface DistributionService {

    /**
     * Collect bib for a participant
     * Records who collected the bib (participant or someone else) and which staff member distributed it
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param request The collect bib request (optional collector details)
     * @param currentUser The authenticated staff user
     * @return Bib distribution details
     */
    BibDistributionResponse collectBib(Long eventId, String bibNumber, CollectBibRequest request, User currentUser);

    /**
     * Undo bib collection for a participant
     * Resets all bib collection fields and clears all goodies distribution
     * Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER (NOT DISTRIBUTOR)
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param currentUser The authenticated user
     * @return Undo operation details
     */
    UndoDistributionResponse undoBib(Long eventId, String bibNumber, User currentUser);

    /**
     * Distribute a goodies item to a participant
     * Requires bib to be collected first
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param request The distribute goodies request with item name
     * @param currentUser The authenticated staff user
     * @return Goodies distribution details
     */
    GoodiesDistributionResponse distributeGoodies(Long eventId, String bibNumber, DistributeGoodiesRequest request, User currentUser);

    /**
     * Get paginated list of participants with pending bib collection for an event
     * @param eventId The event ID
     * @param limit Maximum number of items to return (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token from previous response
     * @param currentUser The authenticated user
     * @return Paginated response with participants who have not collected their bibs
     */
    PendingBibListResponse getPendingBibs(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser);

    /**
     * Get all distribution event logs for an event
     * Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN
     * @param eventId The event ID
     * @param currentUser The authenticated user
     * @return List of distribution event logs
     */
    List<DistributionLogResponse> getDistributionLogs(Long eventId, User currentUser);

    /**
     * Get distribution event logs for a specific participant
     * Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param currentUser The authenticated user
     * @return List of distribution event logs for the participant
     */
    List<DistributionLogResponse> getParticipantLogs(Long eventId, String bibNumber, User currentUser);

    /**
     * Get distribution status for a specific participant
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param currentUser The authenticated user
     * @return Participant distribution status with bib and goodies information
     */
    ParticipantDistributionResponse getDistributionStatus(Long eventId, String bibNumber, User currentUser);

    /**
     * Get paginated list of participants with pending goodies items
     * Returns participants who have collected bibs but have not collected all goodies
     * @param eventId The event ID
     * @param limit Maximum number of items to return (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token from previous response
     * @param currentUser The authenticated user
     * @return Paginated response with participants who have pending goodies items
     */
    PendingGoodiesListResponse getPendingGoodies(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser);

    /**
     * Bulk collect bibs for multiple participants with the same collector
     * @param eventId The event ID
     * @param request Bulk collection request with bib numbers and collector details
     * @param currentUser The authenticated staff user
     * @return Bulk operation result with successful and failed operations
     */
    BulkDistributionResponse bulkCollectBib(Long eventId, BulkCollectBibRequest request, User currentUser);

    /**
     * Bulk distribute goodies items to multiple participants
     * @param eventId The event ID
     * @param request Bulk distribution request with list of bib numbers and item names
     * @param currentUser The authenticated staff user
     * @return Bulk operation result with successful and failed operations
     */
    BulkDistributionResponse bulkDistributeGoodies(Long eventId, BulkDistributeGoodiesRequest request, User currentUser);
}
