package com.timekeeper.bibexpo.demo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Fabricated runner shown on the demo bib and the phone view")
public class DemoRunnerResponse {

    @Schema(description = "Runner name", example = "Asha Verma")
    private String name;

    @Schema(description = "Bib number", example = "12044")
    private String bib;

    @Schema(description = "Race category", example = "F 30–34")
    private String category;
}
