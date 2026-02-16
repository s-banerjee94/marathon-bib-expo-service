package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.CategoryAlreadyExistsException;
import com.timekeeper.bibexpo.exception.CategoryInUseException;
import com.timekeeper.bibexpo.exception.CategoryNotFoundException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.RaceNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Gender;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.CategoryService;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final ParticipantService participantService;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            RaceRepository raceRepository,
            EventRepository eventRepository,
            EventService eventService,
            @Lazy ParticipantService participantService) {
        this.categoryRepository = categoryRepository;
        this.raceRepository = raceRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.participantService = participantService;
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(Long eventId, Long raceId, CreateCategoryRequest request, User currentUser) {
        log.info("Creating category: {} for race ID: {} in event ID: {} by user: {}",
                request.getCategoryName(), raceId, eventId, currentUser.getUsername());

        Race race = validateRaceAndEvent(eventId, raceId, currentUser);

        if (categoryRepository.existsByCategoryNameAndRaceId(request.getCategoryName(), raceId)) {
            throw new CategoryAlreadyExistsException(
                    "Category with name '" + request.getCategoryName() + "' already exists for this race");
        }

        Category category = Category.builder()
                .categoryName(request.getCategoryName())
                .description(request.getDescription())
                .race(race)
                .build();

        Category savedCategory = categoryRepository.save(category);
        log.info("Successfully created category with ID: {} by user: {}",
                savedCategory.getId(), currentUser.getUsername());

        return CategoryResponse.fromEntity(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long eventId, Long raceId, Long categoryId,
                                          UpdateCategoryRequest request, User currentUser) {
        log.info("Updating category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        Race race = validateRaceAndEvent(eventId, raceId, currentUser);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + categoryId));

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException(
                    "Category with ID: " + categoryId + " does not belong to race with ID: " + raceId);
        }

        if (request.getCategoryName() != null && !request.getCategoryName().isBlank() &&
                !request.getCategoryName().equals(category.getCategoryName())) {
            if (categoryRepository.existsByCategoryNameAndRaceId(request.getCategoryName(), raceId)) {
                throw new CategoryAlreadyExistsException(
                        "Category with name '" + request.getCategoryName() + "' already exists for this race");
            }
            category.setCategoryName(request.getCategoryName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("Successfully updated category with ID: {} by user: {}",
                updatedCategory.getId(), currentUser.getUsername());

        return CategoryResponse.fromEntity(updatedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long eventId, Long raceId, Long categoryId, User currentUser) {
        log.info("Fetching category by ID: {} for race ID: {} in event ID: {} for user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        validateRaceAndEvent(eventId, raceId, currentUser);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + categoryId));

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException(
                    "Category with ID: " + categoryId + " does not belong to race with ID: " + raceId);
        }

        log.info("Successfully fetched category with ID: {} for user: {}",
                category.getId(), currentUser.getUsername());

        return CategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRaceId(Long eventId, Long raceId, Gender gender, User currentUser) {
        log.info("Fetching categories for race ID: {} in event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        validateRaceAndEvent(eventId, raceId, currentUser);

        List<Category> categories = categoryRepository.findByRaceId(raceId);

        List<CategoryResponse> categoryResponses = categories.stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());

        log.info("Successfully fetched {} categories for race ID: {} by user: {}",
                categoryResponses.size(), raceId, currentUser.getUsername());

        return categoryResponses;
    }

    @Override
    @Transactional
    public void deleteCategory(Long eventId, Long raceId, Long categoryId, User currentUser) {
        log.info("Deleting category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        validateRaceAndEvent(eventId, raceId, currentUser);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + categoryId));

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException(
                    "Category with ID: " + categoryId + " does not belong to race with ID: " + raceId);
        }

        long participantCount = participantService.countParticipantsByCategoryId(eventId, categoryId);
        if (participantCount > 0) {
            log.warn("Cannot delete category with ID: {} - has {} assigned participants", categoryId, participantCount);
            throw new CategoryInUseException(
                    "Cannot delete category '" + category.getCategoryName() + "'. " +
                    "It has " + participantCount + " participant(s) assigned. " +
                    "Please reassign or delete the participants first.");
        }

        categoryRepository.delete(category);
        log.info("Successfully deleted category with ID: {} by user: {}",
                categoryId, currentUser.getUsername());
    }

    private Race validateRaceAndEvent(Long eventId, Long raceId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new RaceNotFoundException("Race not found with ID: " + raceId));

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException("Race with ID: " + raceId + " does not belong to event with ID: " + eventId);
        }

        return race;
    }

    private void validateUserAuthorizationForEvent(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access categories from their own organization's events");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access categories");
    }

    @Override
    @Transactional(readOnly = true)
    public Category findByRaceIdAndCategoryName(Long raceId, String categoryName, User currentUser) {
        log.info("Finding category by race ID: {} and category name: {} by user: {}",
                raceId, categoryName, currentUser.getUsername());

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new RaceNotFoundException("Race not found with ID: " + raceId));

        Event event = eventRepository.findById(race.getEvent().getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + race.getEvent().getId()));

        validateUserAuthorizationForEvent(currentUser, event);

        return categoryRepository.findByCategoryNameAndRaceId(categoryName, raceId)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category with name '" + categoryName + "' not found for race with ID: " + raceId));
    }
}
