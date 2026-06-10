package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.*;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.CategoryService;
import com.timekeeper.bibexpo.service.ParticipantService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.util.NameNormalizer;
import com.timekeeper.bibexpo.util.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final ParticipantService participantService;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            RaceRepository raceRepository,
            EventRepository eventRepository,
            EventAccessValidator eventAccessValidator,
            @Lazy ParticipantService participantService) {
        this.categoryRepository = categoryRepository;
        this.raceRepository = raceRepository;
        this.eventRepository = eventRepository;
        this.eventAccessValidator = eventAccessValidator;
        this.participantService = participantService;
    }

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.CREATE)
    @Override
    @Transactional
    public CategoryResponse createCategory(Long eventId, Long raceId, CreateCategoryRequest request, User currentUser) {
        log.info("Creating category: {} for race ID: {} in event ID: {} by user: {}",
                request.getCategoryName(), raceId, eventId, currentUser.getUsername());

        Race race = validateRaceAndEvent(eventId, raceId, currentUser);

        String categoryName = NameNormalizer.toStoredName(request.getCategoryName());
        if (categoryRepository.existsByCategoryNameAndRaceId(categoryName, raceId)) {
            throw new CategoryAlreadyExistsException(
                    "A category with this name already exists for this race.");
        }

        Category category = Category.builder()
                .categoryName(categoryName)
                .description(request.getDescription())
                .race(race)
                .build();

        Category savedCategory = categoryRepository.save(category);
        log.info("Successfully created category with ID: {} by user: {}",
                savedCategory.getId(), currentUser.getUsername());

        return CategoryResponse.fromEntity(savedCategory);
    }

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public CategoryResponse updateCategory(Long eventId, Long raceId, Long categoryId,
                                          UpdateCategoryRequest request, User currentUser) {
        log.info("Updating category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        validateRaceAndEvent(eventId, raceId, currentUser);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException();
        }

        String newCategoryName = NameNormalizer.toStoredName(request.getCategoryName());
        if (newCategoryName != null && !newCategoryName.isBlank() &&
                !newCategoryName.equals(category.getCategoryName())) {
            if (categoryRepository.existsByCategoryNameAndRaceId(newCategoryName, raceId)) {
                throw new CategoryAlreadyExistsException(
                        "A category with this name already exists for this race.");
            }
            category.setCategoryName(newCategoryName);
        }

        TextUtils.applyIfSent(request.getDescription(), category::setDescription);

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
                .orElseThrow(CategoryNotFoundException::new);

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException();
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
                .toList();

        log.info("Successfully fetched {} categories for race ID: {} by user: {}",
                categoryResponses.size(), raceId, currentUser.getUsername());

        return categoryResponses;
    }

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCategory(Long eventId, Long raceId, Long categoryId, User currentUser) {
        log.info("Deleting category with ID: {} for race ID: {} in event ID: {} by user: {}",
                categoryId, raceId, eventId, currentUser.getUsername());

        Race race = validateRaceAndEvent(eventId, raceId, currentUser);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException();
        }

        long participantCount = participantService.countParticipantsByCategoryId(eventId, categoryId);
        if (participantCount > 0) {
            log.warn("Cannot delete category with ID: {} - has {} assigned participants", categoryId, participantCount);
            throw new CategoryInUseException(
                    "Cannot delete this category. It has " + participantCount + " participant(s) assigned. Reassign or remove them first.");
        }

        Long orgId = race.getEvent() != null && race.getEvent().getOrganization() != null
                ? race.getEvent().getOrganization().getId() : null;
        AuditContextHolder.setEntityLabel(category.getCategoryName());
        AuditContextHolder.setOrganizationId(orgId);

        categoryRepository.delete(category);
        log.info("Successfully deleted category with ID: {} by user: {}",
                categoryId, currentUser.getUsername());
    }

    private Race validateRaceAndEvent(Long eventId, Long raceId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findById(raceId)
                .orElseThrow(RaceNotFoundException::new);

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }

        return race;
    }

    @Override
    @Transactional(readOnly = true)
    public Category findByRaceIdAndCategoryName(Long raceId, String categoryName, User currentUser) {
        log.info("Finding category by race ID: {} and category name: {} by user: {}",
                raceId, categoryName, currentUser.getUsername());

        Race race = raceRepository.findById(raceId)
                .orElseThrow(RaceNotFoundException::new);

        Event event = eventRepository.findById(race.getEvent().getId())
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return categoryRepository.findByCategoryNameAndRaceId(categoryName, raceId)
                .orElseThrow(CategoryNotFoundException::new);
    }
}
