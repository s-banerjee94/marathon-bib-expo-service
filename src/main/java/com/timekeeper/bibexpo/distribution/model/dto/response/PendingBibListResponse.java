package com.timekeeper.bibexpo.distribution.model.dto.response;

import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
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
