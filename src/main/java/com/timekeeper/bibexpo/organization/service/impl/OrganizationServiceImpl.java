package com.timekeeper.bibexpo.organization.service.impl;

import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.organization.api.OrganizationMemberPurger;
import com.timekeeper.bibexpo.organization.exception.LimitReductionException;
import com.timekeeper.bibexpo.organization.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.organization.exception.OrganizationDeletionNotAllowedException;
import com.timekeeper.bibexpo.organization.exception.OrganizationDisabledException;
import com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.organization.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.InventoryQuotaRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UpdateOrganizationSubscriptionRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UserQuotaRequest;
import com.timekeeper.bibexpo.organization.model.dto.response.MembersUnlockedResponse;
import com.timekeeper.bibexpo.organization.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.organization.model.entity.Organization;
import com.timekeeper.bibexpo.organization.model.entity.OrganizationLimit;
import com.timekeeper.bibexpo.organization.model.enums.SubscriptionTier;
import com.timekeeper.bibexpo.organization.model.event.OrganizationCreatedEvent;
import com.timekeeper.bibexpo.organization.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.organization.repository.OrganizationRepository;
import com.timekeeper.bibexpo.organization.service.cache.OrganizationCache;
import com.timekeeper.bibexpo.organization.service.OrganizationService;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.storage.exception.InvalidFileException;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.storage.model.enums.UploadCategory;
import com.timekeeper.bibexpo.storage.service.StorageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationServiceImpl implements OrganizationService {

    // Legacy stored tier value tolerated as baseline; current tiers are in SubscriptionTier.
    private static final String LEGACY_FREE_TIER = "FREE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FREE = "FREE";
    private static final int SUBSCRIPTION_TERM_YEARS = 1;

    private final OrganizationRepository organizationRepository;
    private final OrganizationLimitRepository organizationLimitRepository;
    private final EventStore eventStore;
    private final OrganizationMemberPurger memberPurger;
    private final StorageService storageService;
    private final OrganizationCache organizationCache;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getAllOrganizations(
            Boolean enabled, String search, Pageable pageable, CurrentActor actor) {
        if (actor.role() != UserRole.ROOT && actor.role() != UserRole.ADMIN) {
            throw new AccessForbiddenException("You are not allowed to view all organizations.");
        }

        Page<Organization> organizationsPage =
                organizationRepository.findAll(buildOrganizationSpecification(enabled, search), pageable);

        // Batch-load limits for this page in one query to avoid N+1
        List<Long> orgIds = organizationsPage.getContent().stream()
                .map(Organization::getId)
                .toList();
        Map<Long, OrganizationLimit> limitsByOrgId = organizationLimitRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(OrganizationLimit::getOrganizationId, Function.identity()));

        return organizationsPage.map(org -> buildResponse(org, limitsByOrgId.get(org.getId())));
    }

    /** Filters by enabled status and matches the search text against name, email or phone. */
    private Specification<Organization> buildOrganizationSpecification(Boolean enabled, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("organizerName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("phoneNumber")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.CREATE)
    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        validateUniqueness(request.getOrganizerName(), request.getEmail(),
                request.getPhoneNumber(), request.getTaxId(), null);

        Organization organization = Organization.builder()
                .organizerName(request.getOrganizerName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .website(request.getWebsite())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .taxId(request.getTaxId())
                .registrationNumber(request.getRegistrationNumber())
                .subscriptionTier(TextUtils.trimToNull(request.getSubscriptionTier()))
                .billingEmail(TextUtils.trimToNull(request.getBillingEmail()))
                .enabled(true)
                .build();

        // New organizations start from no prior subscription, so a paid tier opens a fresh term.
        reconcileSubscriptionState(organization, null);

        Organization savedOrganization = organizationRepository.save(organization);

        // Entity defaults apply, request caps override
        OrganizationLimit limit = OrganizationLimit.builder()
                .organization(savedOrganization)
                .build();
        applyQuotaCaps(limit, request.getUserQuota());
        applyInventoryCaps(limit, request.getInventoryQuota());
        OrganizationLimit savedLimit = organizationLimitRepository.save(limit);
        eventPublisher.publishEvent(new OrganizationCreatedEvent(savedOrganization.getId()));

        log.atInfo()
                .addKeyValue("event", "organization.created")
                .addKeyValue("targetOrgId", savedOrganization.getId())
                .addKeyValue("subscriptionTier", savedOrganization.getSubscriptionTier())
                .log("Organization created");
        return buildResponse(savedOrganization, savedLimit);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request, CurrentActor actor) {
        requireOwnOrganization(actor, id);
        UserRole role = actor.role();
        if (role != UserRole.ROOT && role != UserRole.ADMIN && role != UserRole.ORGANIZER_ADMIN) {
            throw new AccessForbiddenException("You are not allowed to change your organization's details.");
        }
        Organization organization = requireOrganization(id);
        validateUniqueness(request.getOrganizerName(), request.getEmail(),
                request.getPhoneNumber(), request.getTaxId(), organization);

        applyOrganizationUpdates(organization, request);
        Organization updatedOrganization = organizationRepository.save(organization);
        organizationCache.evict(id);

        log.atInfo()
                .addKeyValue("event", "organization.updated")
                .addKeyValue("targetOrgId", id)
                .log("Organization updated");
        return toResponse(updatedOrganization);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public OrganizationResponse updateSubscription(Long id, UpdateOrganizationSubscriptionRequest request) {
        Organization organization = requireOrganization(id);
        OrganizationLimit limit = getOrCreateLimit(organization);
        validateUserLimits(limit, request.getUserQuota());
        validateInventoryLimits(limit, request.getInventoryQuota());

        String previousTier = organization.getSubscriptionTier();
        TextUtils.applyIfSent(request.getSubscriptionTier(), organization::setSubscriptionTier);
        reconcileSubscriptionState(organization, previousTier);
        applyQuotaCaps(limit, request.getUserQuota());
        applyInventoryCaps(limit, request.getInventoryQuota());

        Organization updatedOrganization = organizationRepository.save(organization);
        OrganizationLimit updatedLimit = organizationLimitRepository.save(limit);
        organizationCache.evict(id);

        log.atInfo()
                .addKeyValue("event", "organization.subscription.changed")
                .addKeyValue("targetOrgId", id)
                .addKeyValue("fromTier", previousTier)
                .addKeyValue("toTier", updatedOrganization.getSubscriptionTier())
                .addKeyValue("limitsChanged", request.getUserQuota() != null || request.getInventoryQuota() != null)
                .log("Organization subscription changed");
        return buildResponse(updatedOrganization, updatedLimit);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public OrganizationResponse toggleOrganizationStatus(Long id, Boolean enabled) {
        Organization organization = requireOrganization(id);
        organization.setEnabled(enabled);
        Organization updatedOrganization = organizationRepository.save(organization);
        organizationCache.evict(updatedOrganization.getId());

        // Disabling locks the members; enabling leaves them locked until unlockMembers is called.
        if (Boolean.FALSE.equals(enabled)) {
            memberPurger.lockMembers(id);
        }

        log.atInfo()
                .addKeyValue("event", "organization.enabled.changed")
                .addKeyValue("targetOrgId", id)
                .addKeyValue("enabled", enabled)
                .log("Organization status changed");
        return toResponse(updatedOrganization);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public MembersUnlockedResponse unlockMembers(Long id) {
        Organization organization = requireOrganization(id);
        if (Boolean.FALSE.equals(organization.getEnabled())) {
            throw new OrganizationDisabledException("Enable this organization before unlocking its users.");
        }
        return new MembersUnlockedResponse(memberPurger.unlockMembersLockedByOrganization(id));
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteOrganization(Long id, CurrentActor actor) {
        Organization organization = requireOrganization(id);

        if (eventStore.countByOrganizationId(id) > 0) {
            throw new OrganizationDeletionNotAllowedException(
                    "You cannot delete this organization while it still has events. Delete them first.");
        }

        AuditContextHolder.setEntityLabel(organization.getOrganizerName());
        AuditContextHolder.setOrganizationId(organization.getId());

        String logoKey = organization.getLogoKey();
        int membersPurged = memberPurger.purgeMembers(id);

        // The organization_limits row shares the organization's PK via an FK with no cascade, so it
        // must be removed (and flushed) before the organization itself to avoid a constraint violation.
        organizationLimitRepository.deleteById(id);
        organizationLimitRepository.flush();
        organizationRepository.delete(organization);
        organizationCache.evict(id);
        deleteQuietly(logoKey);
        log.atInfo()
                .addKeyValue("event", "organization.deleted")
                .addKeyValue("targetOrgId", id)
                .addKeyValue("membersPurged", membersPurged)
                .log("Organization deleted");
    }

    // Runs before the organization is loaded, and answers another organization's id exactly like a
    // missing one, so an organization-scoped caller cannot learn which ids exist.
    private void requireOwnOrganization(CurrentActor actor, Long organizationId) {
        if (actor.role() != UserRole.ROOT && actor.role() != UserRole.ADMIN
                && !organizationId.equals(actor.organizationId())) {
            log.atWarn()
                    .addKeyValue("event", "authz.denied")
                    .addKeyValue("reason", "organization_not_own")
                    .addKeyValue("targetOrgId", organizationId)
                    .log("Organization access hidden as not found");
            throw new OrganizationNotFoundException();
        }
    }

    /** Rejects each sent identifying value another organization already holds; {@code current} is null on create. */
    private void validateUniqueness(String name, String email, String phone, String taxId, Organization current) {
        rejectIfTaken(name, current == null ? null : current.getOrganizerName(),
                organizationRepository::existsByOrganizerName, "An organization with this name already exists.");
        rejectIfTaken(email, current == null ? null : current.getEmail(),
                organizationRepository::existsByEmail, "An organization with this email already exists.");
        rejectIfTaken(phone, current == null ? null : current.getPhoneNumber(),
                organizationRepository::existsByPhoneNumber, "An organization with this phone number already exists.");
        rejectIfTaken(taxId, current == null ? null : current.getTaxId(),
                organizationRepository::existsByTaxId, "An organization with this tax ID already exists.");
    }

    private void rejectIfTaken(String value, String currentValue, java.util.function.Predicate<String> exists, String message) {
        if (value != null && !value.isBlank() && !value.equals(currentValue) && exists.test(value)) {
            throw new OrganizationAlreadyExistsException(message);
        }
    }

    private void validateUserLimits(OrganizationLimit limit, UserQuotaRequest quota) {
        if (quota == null) {
            return;
        }
        rejectIfBelowUsage(requestedMax(quota.getAdmins()), limit.getUsedAdmins(),
                "You cannot reduce the administrator limit below the current number of administrators (%d).");
        rejectIfBelowUsage(requestedMax(quota.getOrganizerUsers()), limit.getUsedOrganizerUsers(),
                "You cannot reduce the user limit below the current number of users (%d).");
        rejectIfBelowUsage(requestedMax(quota.getDistributors()), limit.getUsedDistributors(),
                "You cannot reduce the distributor limit below the current number of distributors (%d).");
    }

    // Only the four counted caps can be checked against usage. The other three are ceilings on a
    // single item or attribute: lowering one leaves the rows already over it alone and simply
    // stops them growing, which is the intended effect of tightening a plan.
    private void validateInventoryLimits(OrganizationLimit limit, InventoryQuotaRequest quota) {
        if (quota == null) {
            return;
        }
        rejectIfBelowUsage(quota.getTerms(), limit.getUsedInventoryTerms(),
                "You cannot reduce the inventory vocabulary limit below the number of entries in use (%d).");
        rejectIfBelowUsage(quota.getLocations(), limit.getUsedInventoryLocations(),
                "You cannot reduce the inventory location limit below the number of locations in use (%d).");
        rejectIfBelowUsage(quota.getItems(), limit.getUsedInventoryItems(),
                "You cannot reduce the inventory item limit below the number of items in use (%d).");
        rejectIfBelowUsage(quota.getAttributes(), limit.getUsedInventoryAttributes(),
                "You cannot reduce the inventory attribute limit below the number of attributes in use (%d).");
    }

    /**
     * Rejects a cap change that would drop below the slots already in use.
     * A null new limit leaves the cap unchanged.
     */
    private void rejectIfBelowUsage(Integer newLimit, Integer used, String messageTemplate) {
        int inUse = used != null ? used : 0;
        if (newLimit != null && newLimit < inUse) {
            throw new LimitReductionException(String.format(messageTemplate, inUse));
        }
    }

    // Absent (null) fields are left as they are and a blank optional field clears it; the required
    // name and email can never be cleared.
    private void applyOrganizationUpdates(Organization organization, UpdateOrganizationRequest request) {
        TextUtils.applyRequiredIfSent(request.getOrganizerName(), organization::setOrganizerName);
        TextUtils.applyRequiredIfSent(request.getEmail(), organization::setEmail);
        TextUtils.applyIfSent(request.getPhoneNumber(), organization::setPhoneNumber);
        TextUtils.applyIfSent(request.getWebsite(), organization::setWebsite);
        TextUtils.applyIfSent(request.getAddressLine1(), organization::setAddressLine1);
        TextUtils.applyIfSent(request.getAddressLine2(), organization::setAddressLine2);
        TextUtils.applyIfSent(request.getCity(), organization::setCity);
        TextUtils.applyIfSent(request.getStateProvince(), organization::setStateProvince);
        TextUtils.applyIfSent(request.getPostalCode(), organization::setPostalCode);
        TextUtils.applyIfSent(request.getCountry(), organization::setCountry);
        TextUtils.applyIfSent(request.getTaxId(), organization::setTaxId);
        TextUtils.applyIfSent(request.getRegistrationNumber(), organization::setRegistrationNumber);
        TextUtils.applyIfSent(request.getBillingEmail(), organization::setBillingEmail);
    }

    /**
     * Derives subscription status and term dates from the organization's tier. PAY_AS_YOU_GO is the
     * baseline (a null/blank tier is normalized to it): status FREE, no term dates, no committed
     * subscription. PREMIUM or PARTNER is a committed subscription; opening one from the baseline (or
     * with no term recorded yet) activates a one-year term from now. An unchanged subscription tier
     * keeps its current status — including EXPIRED set by the expiry job — and term, so unrelated
     * edits never silently reactivate a lapsed subscription. When a PREMIUM/PARTNER term lapses the
     * organization falls back to PAY_AS_YOU_GO (manually now; via the deferred expiry job later).
     *
     * @param organization the organization being reconciled, mutated in place
     * @param previousTier  the tier held before this change
     */
    private void reconcileSubscriptionState(Organization organization, String previousTier) {
        if (isBaselineTier(organization.getSubscriptionTier())) {
            organization.setSubscriptionTier(SubscriptionTier.PAY_AS_YOU_GO.name());
            organization.setSubscriptionStatus(STATUS_FREE);
            organization.setSubscriptionStartDate(null);
            organization.setSubscriptionEndDate(null);
            return;
        }
        if (isBaselineTier(previousTier) || organization.getSubscriptionStartDate() == null) {
            organization.setSubscriptionStatus(STATUS_ACTIVE);
            LocalDateTime start = LocalDateTime.now();
            organization.setSubscriptionStartDate(start);
            organization.setSubscriptionEndDate(start.plusYears(SUBSCRIPTION_TERM_YEARS));
        }
    }

    /** Baseline (no committed subscription): PAY_AS_YOU_GO, no tier (null/blank), or legacy "FREE". */
    private boolean isBaselineTier(String tier) {
        return tier == null || tier.isBlank()
                || SubscriptionTier.PAY_AS_YOU_GO.name().equalsIgnoreCase(tier.trim())
                || LEGACY_FREE_TIER.equalsIgnoreCase(tier.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id, CurrentActor actor) {
        requireOwnOrganization(actor, id);
        Organization organization = organizationCache.findById(id);
        if (organization == null) {
            throw new OrganizationNotFoundException();
        }
        return toResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentUserOrganization(CurrentActor actor) {
        if (actor.organizationId() == null) {
            throw new AccessForbiddenException("Your account is not assigned to an organization.");
        }
        Organization organization = organizationCache.findById(actor.organizationId());
        if (organization == null) {
            throw new OrganizationNotFoundException();
        }
        return toResponse(organization);
    }

    /**
     * Builds a response for a single organization, loading its limits row.
     */
    private OrganizationResponse toResponse(Organization organization) {
        OrganizationLimit limit = organizationLimitRepository.findById(organization.getId()).orElse(null);
        return buildResponse(organization, limit);
    }

    /**
     * Single place that maps an organization to a response and presigns a short-lived
     * URL for its logo, so the stored object key is never exposed directly.
     */
    private OrganizationResponse buildResponse(Organization organization, OrganizationLimit limit) {
        OrganizationResponse response = OrganizationResponse.fromEntity(organization, limit);
        response.setLogoUrl(storageService.createDownloadUrl(organization.getLogoKey()));
        return response;
    }

    /**
     * Loads the organization's limits row, or builds a fresh one bound to the
     * organization (with default caps) when none exists yet.
     */
    private OrganizationLimit getOrCreateLimit(Organization organization) {
        return organizationLimitRepository.findById(organization.getId())
                .orElseGet(() -> OrganizationLimit.builder().organization(organization).build());
    }

    /**
     * Applies the request's caps to the limits row. A null quota, role, or cap
     * leaves the corresponding cap untouched, so on create the entity defaults
     * survive and on update the existing caps survive.
     */
    private void applyQuotaCaps(OrganizationLimit limit, UserQuotaRequest quota) {
        if (quota == null) {
            return;
        }
        applyCap(quota.getAdmins(), limit::setMaxAdmins);
        applyCap(quota.getOrganizerUsers(), limit::setMaxOrganizerUsers);
        applyCap(quota.getDistributors(), limit::setMaxDistributors);
    }

    private void applyInventoryCaps(OrganizationLimit limit, InventoryQuotaRequest quota) {
        if (quota == null) {
            return;
        }
        applyCap(quota.getTerms(), limit::setMaxInventoryTerms);
        applyCap(quota.getLocations(), limit::setMaxInventoryLocations);
        applyCap(quota.getItems(), limit::setMaxInventoryItems);
        applyCap(quota.getAttributes(), limit::setMaxInventoryAttributes);
        applyCap(quota.getOptionsPerAttribute(), limit::setMaxInventoryAttributeOptions);
        applyCap(quota.getVariantAttributesPerItem(), limit::setMaxVariantAttributesPerItem);
        applyCap(quota.getVariantsPerItem(), limit::setMaxItemVariants);
    }

    private void applyCap(UserQuotaRequest.RoleQuotaRequest role, Consumer<Integer> setter) {
        applyCap(requestedMax(role), setter);
    }

    private void applyCap(Integer max, Consumer<Integer> setter) {
        if (max != null) {
            setter.accept(max);
        }
    }

    private Integer requestedMax(UserQuotaRequest.RoleQuotaRequest role) {
        return role != null ? role.getMax() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createLogoUploadUrl(Long id, String contentType, CurrentActor actor) {
        requireOwnOrganization(actor, id);
        Organization organization = requireOrganization(id);
        return storageService.createUploadUrl(UploadCategory.ORGANIZATION_LOGO, organization.getId(), contentType);
    }

    @Override
    @Transactional
    public OrganizationResponse attachLogo(Long id, String objectKey, CurrentActor actor) {
        requireOwnOrganization(actor, id);
        Organization organization = requireOrganization(id);

        if (UploadCategory.ORGANIZATION_LOGO.isForeignKeyFor(organization.getId(), objectKey)) {
            throw new InvalidFileException("This upload does not belong to this organization.");
        }
        if (!storageService.objectExists(objectKey)) {
            throw new InvalidFileException("The uploaded file could not be found.");
        }

        String previousKey = organization.getLogoKey();
        organization.setLogoKey(objectKey);
        Organization saved = organizationRepository.saveAndFlush(organization);
        organizationCache.evict(saved.getId());
        if (previousKey != null && !previousKey.equals(objectKey)) {
            deleteQuietly(previousKey);
        }
        logLogoChanged(id, "attached");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationResponse removeLogo(Long id, CurrentActor actor) {
        requireOwnOrganization(actor, id);
        Organization organization = requireOrganization(id);

        String previousKey = organization.getLogoKey();
        organization.setLogoKey(null);
        Organization saved = organizationRepository.saveAndFlush(organization);
        organizationCache.evict(saved.getId());
        deleteQuietly(previousKey);
        logLogoChanged(id, "removed");
        return toResponse(saved);
    }

    private void logLogoChanged(Long id, String action) {
        log.atInfo()
                .addKeyValue("event", "organization.logo.changed")
                .addKeyValue("targetOrgId", id)
                .addKeyValue("action", action)
                .log("Organization logo changed");
    }

    private Organization requireOrganization(Long id) {
        return organizationRepository.findById(id).orElseThrow(OrganizationNotFoundException::new);
    }

    /** Best-effort object deletion: orphaned objects are tolerable, a failed cleanup must not roll back the entity change. */
    private void deleteQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue("event", "storage.delete.failed")
                    .addKeyValue("objectKey", objectKey)
                    .addKeyValue("exception", e.getClass().getSimpleName())
                    .log("Stored object could not be deleted");
        }
    }
}
