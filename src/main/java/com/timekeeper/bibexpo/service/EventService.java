package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.exception.EventDeletionNotAllowedException;
import com.timekeeper.bibexpo.exception.EventDisabledException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
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
     * @param search Search across event name, description, and venue name (partial match, case-insensitive, null for all)
     * @param pageable Pagination and sorting parameters
     * @param currentUser The authenticated user
     * @return Page of event responses
     */
    Page<EventResponse> getAllEvents(Long organizationId, EventStatus status, String search, Pageable pageable, User currentUser);

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
     * - Only ROOT and ADMIN can toggle event status
     * When an event is disabled, all event and child entity endpoints become inaccessible
     * @param id The event ID
     * @param currentUser The authenticated user
     * @return The updated event response
     */
    EventResponse toggleEventEnabled(Long id, User currentUser);

    /**
     * Change event status
     * Authorization:
     * - ROOT and ADMIN can change status for any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only change status for events in their organization
     * - DISTRIBUTOR cannot change event status
     * @param id The event ID
     * @param status The new event status (DRAFT, PUBLISHED, CANCELLED, COMPLETED)
     * @param currentUser The authenticated user
     * @return The updated event response
     * @throws EventNotFoundException if the event does not exist
     * @throws UnauthorizedAccessException if the user lacks permission
     */
    EventResponse changeEventStatus(Long id, EventStatus status, User currentUser);

    /**
     * Validate that an event is enabled and accessible
     * ROOT and ADMIN can access disabled events
     * Other roles cannot access disabled events
     * @param event The event to validate
     * @param currentUser The authenticated user
     * @throws EventDisabledException if the event is disabled and user is not ROOT/ADMIN
     */
    void validateEventEnabled(com.timekeeper.bibexpo.model.entity.Event event, User currentUser);

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
     * Create a presigned S3 upload URL for an event's logo. Authorization mirrors
     * {@link #updateEvent}: ROOT/ADMIN for any event, ORGANIZER_ADMIN/ORGANIZER_USER
     * for events in their own organization.
     * @param id The event ID
     * @param contentType MIME type of the file (validated against allowed image types)
     * @param currentUser The authenticated user
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws EventNotFoundException if the event does not exist
     * @throws UnauthorizedAccessException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException if the content type is not allowed
     */
    PresignUploadResponse createLogoUploadUrl(Long id, String contentType, User currentUser);

    /**
     * Attach a previously uploaded object as the event's logo. Verifies the key belongs
     * to the event and that the object exists, then replaces any previous logo (the old
     * object is deleted).
     * @param id The event ID
     * @param objectKey The object key returned by the presign step
     * @param currentUser The authenticated user
     * @return the updated event response (with a fresh presigned logo URL)
     * @throws EventNotFoundException if the event does not exist
     * @throws UnauthorizedAccessException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException if the key is invalid or the object is missing
     */
    EventResponse attachLogo(Long id, String objectKey, User currentUser);

    /**
     * Remove the event's logo, deleting the object from S3.
     * @param id The event ID
     * @param currentUser The authenticated user
     * @return the updated event response
     * @throws EventNotFoundException if the event does not exist
     * @throws UnauthorizedAccessException if the caller lacks permission
     */
    EventResponse removeLogo(Long id, User currentUser);
}
