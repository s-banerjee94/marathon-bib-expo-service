package com.timekeeper.bibexpo.messaging.direct.model.dto.response;

import com.timekeeper.bibexpo.messaging.direct.model.enums.ParticipantMessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Send outcome for one participant")
public class ParticipantMessageResult {

    @Schema(description = "Bib number the message was addressed to", example = "1234")
    private String bibNumber;

    @Schema(description = "Whether the message reached the provider", example = "SENT")
    private ParticipantMessageStatus status;

    @Schema(description = "Why the send failed; null when the status is SENT",
            example = "This participant does not have a phone number on record.")
    private String reason;
}
