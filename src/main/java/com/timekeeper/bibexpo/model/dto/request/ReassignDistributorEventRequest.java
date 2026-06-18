package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for reassigning a distributor to a different event within its organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reassign a distributor to a different event")
public class ReassignDistributorEventRequest {

    @NotNull(message = "Event is required")
    @Schema(description = "ID of the event to assign the distributor to", example = "10")
    private Long eventId;
}
