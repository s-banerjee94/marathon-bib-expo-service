package com.timekeeper.bibexpo.distribution.service;

import com.timekeeper.bibexpo.distribution.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionGoodieResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.PendingParticipantListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.enums.LogSearchType;
import com.timekeeper.bibexpo.distribution.model.enums.PendingType;
import com.timekeeper.bibexpo.user.model.entity.User;

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
     * Resets all bib collection fields and clears all goodies distribution, putting back any stock
     * those goodies took off the shelf
     * Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER (NOT DISTRIBUTOR)
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param currentUser The authenticated user
     * @return Undo operation details
     */
    UndoDistributionResponse undoBib(Long eventId, String bibNumber, User currentUser);

    /**
     * Distribute goodies to a participant
     * Requires bib to be collected first. Each goody is one on the participant's own list, or one added
     * to the event by hand; one linked to inventory also takes a unit off its location's shelf, of the
     * variant chosen, or else the one the participant's own value reads as
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param request The goodies to hand over, and the variant chosen for any that needs one
     * @param currentUser The authenticated staff user
     * @return Goodies distribution details
     */
    GoodiesDistributionResponse distributeGoodies(Long eventId, String bibNumber, DistributeGoodiesRequest request, User currentUser);

    /**
     * Every goody on the event's list as the counter offers it: where each came from, the inventory
     * item it comes out of, and the variants to choose between for a linked goody whose item has more
     * than one
     * @param eventId The event ID
     * @param currentUser The authenticated staff user
     * @return The event's goodies, in list order
     */
    List<DistributionGoodieResponse> listGoodies(Long eventId, User currentUser);

    /**
     * One page of the event's participants who still have something to collect, in bib order
     * BIB: participants who have not collected their bib. GOODIES: participants who collected their bib
     * but still have goodies of their own to collect; a goody added by hand never makes anyone pending
     * @param eventId The event ID
     * @param type What is still to collect: BIB or GOODIES
     * @param limit Maximum number of items to return (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token from previous response
     * @param currentUser The authenticated user
     * @return Paginated response with the participants, and the goodies each still has to collect
     */
    PendingParticipantListResponse getPending(Long eventId, PendingType type, Integer limit, String lastEvaluatedKey,
                                              User currentUser);

    /**
     * Get paginated distribution event logs for an event
     * Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN
     * @param eventId The event ID
     * @param limit Maximum number of items to return (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token from previous response
     * @param currentUser The authenticated user
     * @return Paginated list of distribution event logs
     */
    DistributionLogListResponse getDistributionLogs(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser);

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

    /**
     * Lookup distribution logs using LSI with begins_with prefix search and pagination
     * @param eventId The event ID
     * @param searchType The LSI to query: BIB, ACTION, PERFORMED_BY, COLLECTOR, ITEM
     * @param searchValue The prefix value to search for
     * @param limit Maximum number of results (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token from previous response
     * @param currentUser The authenticated user
     * @return Paginated list of distribution logs matching the search criteria
     */
    DistributionLogListResponse lookupLogs(Long eventId, LogSearchType searchType, String searchValue,
                                           Integer limit, String lastEvaluatedKey, User currentUser);
}
