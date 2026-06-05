package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request a presigned upload URL for a media file")
public class PresignUploadRequest {

    @NotBlank(message = "Content type is required")
    @Schema(description = "MIME type of the file to upload", example = "image/png")
    private String contentType;
}
