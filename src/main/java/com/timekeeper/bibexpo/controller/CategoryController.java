package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.Gender;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) Gender gender,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get categories for race ID: {} in event ID: {} with gender filter: {} by user: {}",
                raceId, eventId, gender, currentUser.getUsername());

        List<CategoryResponse> categories = categoryService.getCategoriesByRaceId(eventId, raceId, gender, currentUser);

        return ResponseEntity.ok(categories);
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get category by ID: {} for race ID: {} in event ID: {} for user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        CategoryResponse response = categoryService.getCategoryById(eventId, raceId, categoryId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create category: {} for race ID: {} in event ID: {} by user: {}",
                request.getCategoryName(), raceId, eventId, currentUser.getUsername());

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
        log.info("Received request to update category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        CategoryResponse response = categoryService.updateCategory(eventId, raceId, categoryId, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        categoryService.deleteCategory(eventId, raceId, categoryId, currentUser);

        return ResponseEntity.noContent().build();
    }
}
