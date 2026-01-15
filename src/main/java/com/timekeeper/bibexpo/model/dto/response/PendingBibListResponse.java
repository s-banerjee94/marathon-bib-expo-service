package com.timekeeper.bibexpo.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingBibListResponse {
    private List<ParticipantDistributionResponse> participants;
    private String lastEvaluatedKey;
    private Integer count;
    private Boolean hasMore;
}
