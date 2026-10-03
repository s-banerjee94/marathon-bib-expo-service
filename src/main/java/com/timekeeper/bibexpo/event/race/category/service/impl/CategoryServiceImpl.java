package com.timekeeper.bibexpo.event.race.category.service.impl;

import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.event.race.category.exception.CategoryAlreadyExistsException;
import com.timekeeper.bibexpo.event.race.category.exception.CategoryInUseException;
import com.timekeeper.bibexpo.event.race.category.exception.CategoryNotFoundException;
import com.timekeeper.bibexpo.event.api.CategoryUsage;
import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.race.exception.RaceNotFoundException;
import com.timekeeper.bibexpo.event.race.category.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.event.race.category.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.event.race.category.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.event.race.category.model.entity.Category;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.race.model.entity.Race;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.race.category.repository.CategoryRepository;
import com.timekeeper.bibexpo.event.repository.EventRepository;
import com.timekeeper.bibexpo.event.race.repository.RaceRepository;
import com.timekeeper.bibexpo.event.race.category.service.CategoryService;
import com.timekeeper.bibexpo.event.race.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.event.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.shared.util.NameNormalizer;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final CategoryUsage categoryUsage;
    private final EventQuota eventQuota;
    private final EventOperationGuard eventOperationGuard;
    private final RaceCategoryNameResolver nameResolver;

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.CREATE)
    @Override
    @Transactional
    public CategoryResponse createCategory(Long eventId, Long raceId, CreateCategoryRequest request, User currentUser) {
        Race race = validateRaceAndEvent(eventId, raceId, currentUser);
        eventOperationGuard.requireAllowed(race.getEvent(), EventOperation.CATEGORY_WRITE);

        EventLimits limits = eventQuota.forEvent(eventId);
        if (categoryRepository.countByRaceId(raceId) >= limits.maxCategoriesPerRace()) {
            throw new EventLimitExceededException("You have reached the maximum number of categories allowed for this race.");
        }

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
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "category.created")
                .addKeyValue("eventId", eventId)
                .addKeyValue("categoryId", savedCategory.getId())
                .addKeyValue("raceId", raceId)
                .log("Category created");

        return CategoryResponse.fromEntity(savedCategory);
    }

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public CategoryResponse updateCategory(Long eventId, Long raceId, Long categoryId,
                                          UpdateCategoryRequest request, User currentUser) {
        Race updateRace = validateRaceAndEvent(eventId, raceId, currentUser);
        eventOperationGuard.requireAllowed(updateRace.getEvent(), EventOperation.CATEGORY_WRITE);

        Category category = loadCategoryOfRace(categoryId, raceId);

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
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "category.updated")
                .addKeyValue("eventId", eventId)
                .addKeyValue("categoryId", categoryId)
                .addKeyValue("raceId", raceId)
                .log("Category updated");

        return CategoryResponse.fromEntity(updatedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long eventId, Long raceId, Long categoryId, User currentUser) {
        validateRaceAndEvent(eventId, raceId, currentUser);

        return CategoryResponse.fromEntity(loadCategoryOfRace(categoryId, raceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRaceId(Long eventId, Long raceId, User currentUser) {
        validateRaceAndEvent(eventId, raceId, currentUser);

        return categoryRepository.findByRaceId(raceId).stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Auditable(entityType = AuditEntityType.CATEGORY, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCategory(Long eventId, Long raceId, Long categoryId, User currentUser) {
        Race race = validateRaceAndEvent(eventId, raceId, currentUser);
        eventOperationGuard.requireAllowed(race.getEvent(), EventOperation.CATEGORY_WRITE);

        Category category = loadCategoryOfRace(categoryId, raceId);

        long participantCount = categoryUsage.countParticipants(eventId, categoryId);
        if (participantCount > 0) {
            throw new CategoryInUseException("You cannot delete this category while " + participantCount
                    + " participant(s) are assigned to it. Reassign or remove them first.");
        }

        Long orgId = race.getEvent() != null && race.getEvent().getOrganization() != null
                ? race.getEvent().getOrganization().getId() : null;
        AuditContextHolder.setEntityId(String.valueOf(categoryId));
        AuditContextHolder.setEntityLabel(category.getCategoryName());
        AuditContextHolder.setOrganizationId(orgId);

        categoryRepository.delete(category);
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "category.deleted")
                .addKeyValue("eventId", eventId)
                .addKeyValue("categoryId", categoryId)
                .addKeyValue("raceId", raceId)
                .log("Category deleted");
    }

    private Race validateRaceAndEvent(Long eventId, Long raceId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(RaceNotFoundException::new);

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }

        return race;
    }

    // A category belonging to another race reads as not found, so the response never confirms that
    // an id exists outside the race the caller asked about.
    private Category loadCategoryOfRace(Long categoryId, Long raceId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);

        if (!category.getRace().getId().equals(raceId)) {
            throw new CategoryNotFoundException();
        }

        return category;
    }

}
