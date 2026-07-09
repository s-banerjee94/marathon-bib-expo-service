package com.timekeeper.bibexpo.passwordreset.model.dto.response;

import com.timekeeper.bibexpo.messaging.delivery.DeliveryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response returned to an administrator who issued a reset link: the link to share, plus the
 * per-channel delivery outcome when delivery channels were requested (empty for a manual issue).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Issued password-reset link")
public class PasswordResetLinkResponse {

    @Schema(description = "Link the user opens to set a new password",
            example = "https://app.bibexpo.com/reset-password?token=9fK3aN7pQ2rV8sX1dM6yB4cE0hL5tW-zJ8uG3oI2kS")
    private String resetUrl;

    @Schema(description = "Per-channel delivery outcome; empty when no channels were requested")
    private List<DeliveryResult> deliveries;
}
