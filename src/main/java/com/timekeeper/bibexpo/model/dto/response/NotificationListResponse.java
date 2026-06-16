package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One page of notifications for the current user (newest first)")
public class NotificationListResponse {

    @Schema(description = "Notifications in this page, newest first")
    private List<NotificationResponse> items;

    @Schema(description = "Number of notifications returned in this page", example = "20")
    private Integer count;

    @Schema(description = "Opaque cursor for the next page; pass it back as the `cursor` query param. Null when there are no more pages.",
            example = "NDJ8ZXlKMWMyVnlTV1FpT...")
    private String lastEvaluatedKey;

    @Schema(description = "Whether more pages are available", example = "true")
    private Boolean hasMore;
}
