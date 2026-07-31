package com.timekeeper.bibexpo.messaging.direct.model.dto.request;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.validation.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = """
        Request payload for sending one message template to named participants of an event. \
        Use it to re-send a confirmation a participant never received, without running a campaign over the whole event.""")
public class SendParticipantMessagesRequest {

    @NotNull(message = "Channel is required")
    @ValidEnum(enumClass = MessageChannel.class, excludes = {"EMAIL"}, message = "Channel must be SMS or WHATSAPP")
    @Schema(description = "Delivery channel. Email is not available yet.", example = "SMS",
            implementation = String.class, allowableValues = {"SMS", "WHATSAPP"})
    private String channel;

    @Schema(description = "Template to send. Omit to reuse the template of the event's active bib collection campaign for this channel.",
            example = "12345", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long templateId;

    @NotEmpty(message = "At least one bib number is required")
    @Size(max = 25, message = "You can send to a maximum of 25 participants at a time")
    @Schema(description = "Bib numbers of the participants to message. Duplicates are ignored.", example = "[\"1234\", \"1235\"]")
    private List<@NotBlank(message = "Bib number must not be blank") String> bibNumbers;
}
