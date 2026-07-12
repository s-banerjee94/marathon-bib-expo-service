package com.timekeeper.bibexpo.messaging.shared.template;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
public class MessageTemplateContext {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    // Participant fields
    private final String fullName;
    private final String bibNumber;
    private final String raceName;
    private final String categoryName;
    private final String bibCollectedAt;
    private final String bibCollectedByName;
    private final String bibCollectedByPhone;

    // Event fields
    private final String eventName;
    private final String venueName;
    private final String eventStartDate;
    private final String eventEndDate;
    private final String eventCity;

    // Race fields
    private final String reportingTime;

    public MessageTemplateContext(ParticipantDDB participant, Event event, String raceName, String categoryName,
                              Instant reportingTime) {
        this.fullName = participant.getFullName();
        this.bibNumber = participant.getBibNumber();
        this.raceName = raceName;
        this.categoryName = categoryName;
        this.bibCollectedAt = participant.getBibCollectedAt();
        this.bibCollectedByName = participant.getBibCollectedByName();
        this.bibCollectedByPhone = participant.getBibCollectedByPhone();

        this.eventName = event.getEventName();
        this.venueName = event.getVenueName();
        ZoneId zone = event.getTimezone() != null ? ZoneId.of(event.getTimezone()) : ZoneId.of("UTC");
        this.eventStartDate = event.getEventStartDate() != null
                ? event.getEventStartDate().atZone(zone).format(DATE_FORMATTER) : null;
        this.eventEndDate = event.getEventEndDate() != null
                ? event.getEventEndDate().atZone(zone).format(DATE_FORMATTER) : null;
        this.eventCity = event.getCity();
        this.reportingTime = reportingTime != null
                ? reportingTime.atZone(zone).format(DATETIME_FORMATTER) : null;
    }
}
