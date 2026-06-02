package com.timekeeper.bibexpo.util;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import lombok.Getter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
public class SmsTemplateContext {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

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

    public SmsTemplateContext(ParticipantDDB participant, Event event, String raceName, String categoryName) {
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
    }
}
