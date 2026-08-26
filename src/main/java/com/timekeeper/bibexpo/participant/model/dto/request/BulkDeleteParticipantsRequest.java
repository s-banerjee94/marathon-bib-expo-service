package com.timekeeper.bibexpo.participant.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteParticipantsRequest {

    public static final int MAX_BIB_NUMBERS = 25;

    @NotEmpty(message = "BIB numbers list cannot be empty")
    @Size(max = MAX_BIB_NUMBERS, message = "You cannot delete more than 25 participants at once.")
    private List<String> bibNumbers;
}
