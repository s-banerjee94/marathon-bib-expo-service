package com.timekeeper.bibexpo.messaging.template.model.dto.response;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * System message template as returned to the root user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "System message template content for one purpose × channel")
public class SystemMessageTemplateResponse {

    private SystemTemplatePurpose purpose;
    private MessageChannel channel;
    private String body;
    private String variables;
    private String dltTemplateId;
    private String senderId;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
