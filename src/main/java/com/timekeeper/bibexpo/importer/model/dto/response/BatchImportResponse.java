package com.timekeeper.bibexpo.importer.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchImportResponse {
    private Long jobExecutionId;
    private String status;
}
