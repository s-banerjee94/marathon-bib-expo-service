package com.timekeeper.bibexpo.util;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import lombok.Getter;

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

    public SmsTemplateContext(ParticipantDDB participant, Event event) {
        this.fullName = participant.getFullName();
        this.bibNumber = participant.getBibNumber();
        this.raceName = participant.getRaceName();
        this.categoryName = participant.getCategoryName();
        this.bibCollectedAt = participant.getBibCollectedAt();
        this.bibCollectedByName = participant.getBibCollectedByName();
        this.bibCollectedByPhone = participant.getBibCollectedByPhone();

        this.eventName = event.getEventName();
        this.venueName = event.getVenueName();
        this.eventStartDate = event.getEventStartDate() != null
                ? event.getEventStartDate().format(DATE_FORMATTER) : null;
        this.eventEndDate = event.getEventEndDate() != null
                ? event.getEventEndDate().format(DATE_FORMATTER) : null;
        this.eventCity = event.getCity();
    }
}
