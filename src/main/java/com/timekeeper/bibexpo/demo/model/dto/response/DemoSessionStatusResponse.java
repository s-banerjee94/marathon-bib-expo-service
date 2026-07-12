package com.timekeeper.bibexpo.demo.model.dto.response;

import com.timekeeper.bibexpo.demo.model.DemoSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current status of a demo session")
public class DemoSessionStatusResponse {

    @Schema(description = "Session status", example = "COLLECTED")
    private DemoSessionStatus status;
}
