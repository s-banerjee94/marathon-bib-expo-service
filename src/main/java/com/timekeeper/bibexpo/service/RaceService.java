package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

public interface RaceService {

    /**
     * Create a new race for an event
     * Authorization:
     * - ROOT and ADMIN can create races for any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can create races for events in their organization
     * @param eventId The event ID
     * @param request The race creation request
     * @param currentUser The authenticated user
     * @return The created race response
     */
    RaceResponse createRace(Long eventId, CreateRaceRequest request, User currentUser);

    /**
     * Update an existing race
     * Authorization:
     * - ROOT and ADMIN can update any race
     * - ORGANIZER_ADMIN and ORGANIZER_USER can update races in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param request The race update request
     * @param currentUser The authenticated user
     * @return The updated race response
     */
    RaceResponse updateRace(Long eventId, Long raceId, UpdateRaceRequest request, User currentUser);

    /**
     * Get race by ID
     * Authorization:
     * - ROOT and ADMIN can view any race
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view races in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param currentUser The authenticated user
     * @return The race response
     */
    RaceResponse getRaceById(Long eventId, Long raceId, User currentUser);

    /**
     * Get all races for an event
     * Authorization:
     * - ROOT and ADMIN can view races for any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view races in their organization's events
     * @param eventId The event ID
     * @param currentUser The authenticated user
     * @return List of race responses
     */
    List<RaceResponse> getRacesByEventId(Long eventId, User currentUser);

    /**
     * Permanently delete a race
     * Authorization:
     * - ROOT and ADMIN can delete any race
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only delete races in their organization's events
     * Conditions for deletion:
     * - Race must not have any categories
     * @param eventId The event ID
     * @param raceId The race ID
     * @param currentUser The authenticated user
     * @throws RaceNotFoundException if the race does not exist
     * @throws RaceDeletionNotAllowedException if the race has categories
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    void deleteRace(Long eventId, Long raceId, User currentUser);

    /**
     * Find race by event ID and race name
     * @param eventId The event ID
     * @param raceName The race name
     * @param currentUser The authenticated user
     * @return The race entity
     */
    com.timekeeper.bibexpo.model.entity.Race findByEventIdAndRaceName(Long eventId, String raceName, User currentUser);
}
