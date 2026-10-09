package com.timekeeper.bibexpo.event.race.category.controller;

import com.timekeeper.bibexpo.event.race.category.exception.CategoryAlreadyExistsException;
import com.timekeeper.bibexpo.event.race.category.exception.CategoryInUseException;
import com.timekeeper.bibexpo.event.race.category.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.event.race.category.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.event.race.category.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.event.race.category.service.CategoryService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/races/{raceId}/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController implements CategoryControllerApi {

    private final CategoryService categoryService;

    @Override
    public ResponseEntity<List<CategoryResponse>> getCategoriesByRaceId(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser) {
        List<CategoryResponse> categories = categoryService.getCategoriesByRaceId(eventId, raceId, currentUser);

        return ResponseEntity.ok(categories);
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser) {
        CategoryResponse response = categoryService.getCategoryById(eventId, raceId, categoryId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal User currentUser) {
        CategoryResponse response = categoryService.createCategory(eventId, raceId, request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal User currentUser) {
        CategoryResponse response = categoryService.updateCategory(eventId, raceId, categoryId, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser) {
        categoryService.deleteCategory(eventId, raceId, categoryId, currentUser);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCategoryAlreadyExists(
            CategoryAlreadyExistsException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ErrorResponse> handleCategoryInUse(
            CategoryInUseException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    private void logRejected(HttpStatus status, Exception ex) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
    }
}
