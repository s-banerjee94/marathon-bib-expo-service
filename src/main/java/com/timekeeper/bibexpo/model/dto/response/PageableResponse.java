package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic pageable response wrapper following industry standards
 * Provides clear, explicit pagination metadata for API responses
 *
 * @param <T> The type of content in the page
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Pageable response with metadata")
public class PageableResponse<T> {

    @Schema(description = "List of items in the current page", example = "[]")
    private List<T> content;

    @Schema(description = "Current page number (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Number of items per page", example = "10")
    private int size;

    @Schema(description = "Total number of items across all pages", example = "100")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "10")
    private int totalPages;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    @Schema(description = "Whether the page has content", example = "true")
    private boolean hasContent;

    @Schema(description = "Whether the page is empty", example = "false")
    private boolean empty;

    @Schema(description = "Number of items in the current page", example = "10")
    private int numberOfElements;

    /**
     * Create a PageableResponse from a Spring Data Page object
     *
     * @param page Spring Data Page object
     * @param <T>  Type of content
     * @return PageableResponse with all metadata
     */
    public static <T> PageableResponse<T> of(Page<T> page) {
        return PageableResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasContent(page.hasContent())
                .empty(page.isEmpty())
                .numberOfElements(page.getNumberOfElements())
                .build();
    }
}
