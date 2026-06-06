package com.timekeeper.bibexpo.billing.model.dto.request;

import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Set a bill's payment state")
public class UpdatePaymentStatusRequest {

    @NotNull(message = "Payment status is required")
    @Schema(description = "New payment state", example = "PAID")
    private PaymentStatus paymentStatus;
}
