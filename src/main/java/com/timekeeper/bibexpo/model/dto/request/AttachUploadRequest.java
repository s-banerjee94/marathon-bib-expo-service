package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Attach a previously uploaded object to an entity")
public class AttachUploadRequest {

    @NotBlank(message = "Object key is required")
    @Schema(description = "Object key returned by the presign endpoint",
            example = "users/7/profile/3f2c1d8e-....png")
    private String objectKey;
}
