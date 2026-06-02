package com.timekeeper.bibexpo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<String> validationErrors;

    /**
     * Builds a standard error body with the request path filled in. Shared by the global handler and
     * any controller-local {@code @ExceptionHandler} so the response shape stays identical everywhere.
     */
    public static ErrorResponse of(HttpStatus status, String error, String message, WebRequest request) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }
}
