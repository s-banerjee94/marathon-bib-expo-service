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
     * @param fields fields to export, in column order; a repeated field is exported once, and null or
     *               empty exports every field in {@link ExportField} declaration order
     * @param currentUser The authenticated user
     * @return CSV content as byte array
     */
    byte[] exportParticipantsToCsv(Long eventId, List<ExportField> fields, User currentUser);
}
