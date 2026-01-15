package com.timekeeper.bibexpo.model.dto.request;

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

    @NotEmpty(message = "BIB numbers list cannot be empty")
    @Size(max = 25, message = "Cannot delete more than 25 participants at once")
    private List<String> bibNumbers;
}
