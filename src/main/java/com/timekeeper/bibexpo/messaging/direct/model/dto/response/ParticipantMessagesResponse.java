package com.timekeeper.bibexpo.messaging.direct.model.dto.response;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of a targeted send: the template that was used plus one entry per requested bib number")
public class ParticipantMessagesResponse {

    @Schema(description = "Channel the messages were sent over", example = "SMS")
    private MessageChannel channel;

    @Schema(description = "Template that was sent", example = "12345")
    private Long templateId;

    @Schema(description = "Template name", example = "Bib collection confirmation")
    private String templateName;

    @Schema(description = "Number of participants the provider accepted a message for", example = "1")
    private int sentCount;

    @Schema(description = "Number of participants the message could not be sent to", example = "1")
    private int failedCount;

    @Schema(description = "Per-participant outcome, in request order")
    private List<ParticipantMessageResult> results;
}
