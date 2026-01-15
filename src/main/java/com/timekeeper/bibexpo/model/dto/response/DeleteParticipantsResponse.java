package com.timekeeper.bibexpo.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteParticipantsResponse {
    private Long eventId;
    private String eventName;
    private Integer deletedCount;
    private Integer failedCount;
    private String message;
}
