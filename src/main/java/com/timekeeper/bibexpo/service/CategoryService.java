package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.Gender;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

public interface CategoryService {

    /**
     * Create a new category for a race
     * Authorization:
     * - ROOT and ADMIN can create categories for any race
     * - ORGANIZER_ADMIN and ORGANIZER_USER can create categories for races in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param request The category creation request
     * @param currentUser The authenticated user
     * @return The created category response
     */
    CategoryResponse createCategory(Long eventId, Long raceId, CreateCategoryRequest request, User currentUser);

    /**
     * Update an existing category
     * Authorization:
     * - ROOT and ADMIN can update any category
     * - ORGANIZER_ADMIN and ORGANIZER_USER can update categories in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param categoryId The category ID
     * @param request The category update request
     * @param currentUser The authenticated user
     * @return The updated category response
     */
    CategoryResponse updateCategory(Long eventId, Long raceId, Long categoryId, UpdateCategoryRequest request, User currentUser);

    /**
     * Get category by ID
     * Authorization:
     * - ROOT and ADMIN can view any category
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view categories in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param categoryId The category ID
     * @param currentUser The authenticated user
     * @return The category response
     */
    CategoryResponse getCategoryById(Long eventId, Long raceId, Long categoryId, User currentUser);

    /**
     * Get all categories for a race with optional gender filter
     * Authorization:
     * - ROOT and ADMIN can view categories for any race
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only view categories in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param gender Filter by gender (null for all)
     * @param currentUser The authenticated user
     * @return List of category responses
     */
    List<CategoryResponse> getCategoriesByRaceId(Long eventId, Long raceId, Gender gender, User currentUser);

    /**
     * Permanently delete a category
     * Authorization:
     * - ROOT and ADMIN can delete any category
     * - ORGANIZER_ADMIN and ORGANIZER_USER can only delete categories in their organization's events
     * @param eventId The event ID
     * @param raceId The race ID
     * @param categoryId The category ID
     * @param currentUser The authenticated user
     * @throws CategoryNotFoundException if the category does not exist
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    void deleteCategory(Long eventId, Long raceId, Long categoryId, User currentUser);
}
