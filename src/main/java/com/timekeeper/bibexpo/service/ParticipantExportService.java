package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ExportField;

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
