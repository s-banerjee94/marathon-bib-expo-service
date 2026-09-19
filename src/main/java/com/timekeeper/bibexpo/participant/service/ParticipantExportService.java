package com.timekeeper.bibexpo.participant.service;

import com.timekeeper.bibexpo.participant.model.enums.ExportField;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * CSV export of an event's participants.
 */
public interface ParticipantExportService {

    /**
     * Export participants to CSV format with selectable fields
     * @param eventId The event ID
     * @param fields List of fields to include in export (null or empty exports all fields)
     * @param currentUser The authenticated user
     * @return CSV content as byte array
     */
    byte[] exportParticipantsToCsv(Long eventId, List<ExportField> fields, User currentUser);
}
