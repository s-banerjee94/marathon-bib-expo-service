package com.timekeeper.bibexpo.messaging.direct.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Per-participant outcome of a targeted send: SENT = handed to the provider; FAILED = not sent, see reason")
public enum ParticipantMessageStatus {
    SENT,
    FAILED
}
