package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Presigned S3 upload target. PUT the file bytes to uploadUrl with the "
        + "given Content-Type, then call the matching attach endpoint with objectKey.")
public class PresignUploadResponse {

    @Schema(description = "Presigned S3 PUT URL the client uploads the file to",
            example = "https://bucket.s3.ap-south-1.amazonaws.com/users/7/profile/uuid.png?X-Amz-...")
    private String uploadUrl;

    @Schema(description = "Server-generated object key to send back when attaching the upload",
            example = "users/7/profile/3f2c1d8e-....png")
    private String objectKey;

    @Schema(description = "Content-Type header the client must set on the upload PUT",
            example = "image/png")
    private String contentType;

    @Schema(description = "Seconds until the upload URL expires", example = "600")
    private long expiresInSeconds;
}
