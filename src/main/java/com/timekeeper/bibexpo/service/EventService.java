package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {

    /**
     * Create a new event
     * Authorization:
     * - ROOT and ADMIN can create events for any organization
     * - ORGANIZER_ADMIN can create events for their own organization
     * - ORGANIZER_USER can create events for their own organization
     * @param request The event creation request
     * @param currentUser The authenticated user
     * @return The created event response
     */
    EventResponse createEvent(CreateEventRequest request, User currentUser);

    /**
     * Update an existing event
     * Authorization:
     * - ROOT and ADMIN can update events for any organization
     * - ORGANIZER_ADMIN can update events for their own organization
     * - ORGANIZER_USER can update events for their own organization
     * @param id The event ID
     * @param request The event update request
     * @param currentUser The authenticated user
     * @return The updated event response
     */
    EventResponse updateEvent(Long id, UpdateEventRequest request, User currentUser);

    /**
     * Get all events in the system with optional filters and pagination
     * Authorization:
     * - Only ROOT and ADMIN can access this method
     * @param organizationId Filter by organization ID (null for all)
     * @param status Filter by event status (null for all)
     * @param deleted Filter by deleted status (null for all)
     * @param search Search across event name, description, and venue name (partial match, case-insensitive, null for all)
     * @param pageable Pagination and sorting parameters
     * @param currentUser The authenticated user
     * @return Page of event responses
     */
    Page<EventResponse> getAllEvents(Long organizationId, EventStatus status, Boolean deleted, String search, Pageable pageable, User currentUser);

    /**
     * Get all events in the current user's organization with optional filters and pagination
     * Authorization:
     * - Only ORGANIZER_ADMIN and ORGANIZER_USER can access this method
     * - Automatically scoped to the user's organization
     * @param status Filter by event status (null for all)
     * @param search Search across event name, description, and venue name (partial match, case-insensitive, null for all)
     * @param pageable Pagination and sorting parameters
     * @param currentUser The authenticated user
     * @return Page of event responses
     */
    Page<EventResponse> getOrganizationEvents(EventStatus status, String search, Pageable pageable, User currentUser);

    /**
     * Get event by ID
     * Authorization:
     * - ROOT and ADMIN can view any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view events from their own organization
     * @param id The event ID
     * @param currentUser The authenticated user
     * @return The event response
     */
    EventResponse getEventById(Long id, User currentUser);

    /**
     * Toggle event enabled status
     * Authorization:
     * - ROOT and ADMIN can toggle any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only toggle events from their own organization
     * @param id The event ID
     * @param currentUser The authenticated user
     * @return The updated event response
     */
    EventResponse toggleEventEnabled(Long id, User currentUser);

    /**
     * Permanently delete an event
     * Authorization:
     * - ROOT and ADMIN can delete any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only delete events from their own organization
     * Conditions for deletion:
     * - Event status must be DRAFT or CANCELLED
     * - Event participant list must be empty (TODO: to be implemented when participant feature is complete)
     * @param id The event ID
     * @param currentUser The authenticated user
     * @throws EventNotFoundException if the event does not exist
     * @throws EventDeletionNotAllowedException if the event cannot be deleted (status is not DRAFT/CANCELLED or has participants)
     * @throws UnauthorizedAccessException if the user is not authorized to delete the event
     */
    void deleteEvent(Long id, User currentUser);

    /**
     * Get event summary with races and categories
     * Authorization:
     * - ROOT and ADMIN can view any event summary
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view summaries for events in their organization
     * @param id The event ID
     * @param currentUser The authenticated user
     * @return The event summary response with races and categories
     */
    com.timekeeper.bibexpo.model.dto.response.EventSummaryResponse getEventSummary(Long id, User currentUser);
}
