package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.SearchType;

import java.util.List;

public interface ParticipantService {

    /**
     * Create a new participant for an event
     * @param eventId The event ID
     * @param request The create participant request
     * @param currentUser The authenticated user
     * @return Created participant details
     */
    ParticipantResponse createParticipant(Long eventId, CreateParticipantRequest request, User currentUser);

    /**
     * Get all participants for an event with pagination
     * @param eventId The event ID
     * @param limit Maximum number of participants to return
     * @param lastEvaluatedKey DynamoDB pagination key (base64 encoded)
     * @param currentUser The authenticated user
     * @return Paginated participant list
     */
    ParticipantListResponse getParticipantsByEvent(
            Long eventId,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser);

    /**
     * Get participant by bib number
     * @param eventId The event ID
     * @param bibNumber The bib number
     * @param currentUser The authenticated user
     * @return Participant details
     */
    ParticipantResponse getParticipantByBibNumber(Long eventId, String bibNumber, User currentUser);

    /**
     * Update participant details
     * Only non-null fields in the request will be updated
     * BIB number can be changed (requires delete+create operation)
     * @param eventId The event ID
     * @param bibNumber The current bib number
     * @param request The update request with fields to update
     * @param currentUser The authenticated user
     * @return Updated participant details
     */
    ParticipantResponse updateParticipant(Long eventId, String bibNumber,
                                          UpdateParticipantRequest request, User currentUser);

    /**
     * Get participant count for an event
     * @param eventId The event ID
     * @param currentUser The authenticated user
     * @return Total number of participants
     */
    Long getParticipantCount(Long eventId, User currentUser);

    /**
     * Delete a single participant by bib number
     * @param eventId The event ID
     * @param bibNumber The bib number of the participant to delete
     * @param currentUser The authenticated user
     * @return Delete result with confirmation
     */
    DeleteParticipantsResponse deleteParticipant(Long eventId, String bibNumber, User currentUser);

    /**
     * Delete all participants for an event
     * @param eventId The event ID
     * @param currentUser The authenticated user
     * @return Delete a result with count
     */
    DeleteParticipantsResponse deleteAllParticipants(Long eventId, User currentUser);

    /**
     * Delete specific participants by bib numbers
     * @param eventId The event ID
     * @param bibNumbers List of bib numbers to delete (max 25)
     * @param currentUser The authenticated user
     * @return Delete result with count
     */
    DeleteParticipantsResponse deleteBulkParticipants(Long eventId, java.util.List<String> bibNumbers, User currentUser);

    /**
     * Lookup participants using DynamoDB LSI (cost-efficient Query operation)
     * Uses Local Secondary Indexes for efficient lookups by specific field types.
     * Supports begins_with matching for all search types.
     * @param eventId The event ID (partition key)
     * @param searchType The type of search (NAME, EMAIL, PHONE, BIB, RACE, CATEGORY)
     * @param searchValue The value to search for (uses begins_with for LSI queries)
     * @param limit Maximum results (default: 50, max: 100)
     * @param lastEvaluatedKey Pagination token (base64 encoded)
     * @param currentUser The authenticated user
     * @return Paginated lookup results
     */
    ParticipantListResponse lookupParticipants(
            Long eventId,
            SearchType searchType,
            String searchValue,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser
    );

    /**
     * Count participants assigned to a specific category
     * Used for validation before category deletion
     * @param eventId The event ID
     * @param categoryId The category ID
     * @return Number of participants in the category
     */
    long countParticipantsByCategoryId(Long eventId, Long categoryId);

}
