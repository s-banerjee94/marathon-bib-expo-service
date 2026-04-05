package com.timekeeper.bibexpo.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchJobStatusResponse {
    private Long jobExecutionId;
    private String status;
    private Long readCount;
    private Long writeCount;
    private Long skipCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
