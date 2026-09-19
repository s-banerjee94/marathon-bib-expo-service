package com.timekeeper.bibexpo.event.model.dto.response;

import com.timekeeper.bibexpo.event.model.entity.EventGoodie;
import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One goody the event hands out")
public class EventGoodieResponse {

    @Schema(description = "Goody name; for an imported goody, the column heading exactly as the "
            + "import stored it", example = "T-Shirt")
    private String name;

    @Schema(description = "IMPORT when an imported file brought it, so participant records carry "
            + "it; MANUAL when it was added by hand", example = "IMPORT")
    private GoodieSource source;

    public static EventGoodieResponse from(EventGoodie goodie) {
        return new EventGoodieResponse(goodie.name(), goodie.source());
    }
}
