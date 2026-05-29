package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserLimitReductionException;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UserQuotaRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.OrganizationLimit;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.OrganizationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final OrganizationRepository organizationRepository;
    private final OrganizationLimitRepository organizationLimitRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getAllOrganizations(
            Boolean enabled, Boolean deleted, String search, Pageable pageable, User currentUser) {
        log.info("Fetching all organizations with filters - enabled: {}, deleted: {}, search: {}, user: {}",
                enabled, deleted, search, currentUser.getUsername());

        // Only ROOT and ADMIN can access this method (enforced by @PreAuthorize in controller)
        // But we'll add an additional check here for extra security
        if (currentUser.getRole() != UserRole.ROOT && currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedAccessException(
                    "You are not allowed to view all organizations.");
        }

        // Build dynamic specification for filtering
        Specification<Organization> spec = buildOrganizationSpecification(enabled, deleted, search);

        // Fetch organizations with pagination
        Page<Organization> organizationsPage = organizationRepository.findAll(spec, pageable);

        // Batch-load limits for this page in one query to avoid N+1
        List<Long> orgIds = organizationsPage.getContent().stream()
                .map(Organization::getId)
                .toList();
        Map<Long, OrganizationLimit> limitsByOrgId = organizationLimitRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(OrganizationLimit::getOrganizationId, Function.identity()));

        // Convert to response DTOs
        Page<OrganizationResponse> responsePage = organizationsPage.map(
                org -> OrganizationResponse.fromEntity(org, limitsByOrgId.get(org.getId())));

        log.info("Successfully fetched {} organizations (page {} of {})",
                responsePage.getNumberOfElements(),
                responsePage.getNumber() + 1,
                responsePage.getTotalPages());

        return responsePage;
    }

    /**
     * Build dynamic specification for filtering organizations
     */
    private Specification<Organization> buildOrganizationSpecification(
            Boolean enabled, Boolean deleted, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by enabled status if provided
            if (enabled != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), enabled));
            }

            // Filter by deleted status if provided
            if (deleted != null) {
                predicates.add(criteriaBuilder.equal(root.get("deleted"), deleted));
            } else {
                // By default, exclude deleted organizations unless explicitly requested
                predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            }

            // Multi-field search across organizerName, email, and phoneNumber
            // Uses OR logic - matches if ANY field contains the search term
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";

                Predicate organizerNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("organizerName")),
                        searchPattern
                );

                Predicate emailPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("email")),
                        searchPattern
                );

                Predicate phoneNumberPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("phoneNumber")),
                        searchPattern
                );

                // Combine the three predicates with OR
                predicates.add(criteriaBuilder.or(
                        organizerNamePredicate,
                        emailPredicate,
                        phoneNumberPredicate
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.CREATE)
    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        log.info("Creating organization with email: {}", request.getEmail());

        // Check if organization with email already exists
        if (organizationRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new OrganizationAlreadyExistsException(
                    "An organization with this email already exists."
            );
        }

        // Check if organization with name already exists
        if (organizationRepository.existsByOrganizerNameAndDeletedFalse(request.getOrganizerName())) {
            throw new OrganizationAlreadyExistsException(
                    "An organization with this name already exists."
            );
        }

        // Check if organization with phone number already exists (if provided)
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank() && organizationRepository.existsByPhoneNumberAndDeletedFalse(request.getPhoneNumber())) {
            throw new OrganizationAlreadyExistsException(
                    "An organization with this phone number already exists."
            );
        }

        // Check if organization with taxId already exists (if taxId is provided)
        if (request.getTaxId() != null && !request.getTaxId().isBlank() && organizationRepository.existsByTaxIdAndDeletedFalse(request.getTaxId())) {
                throw new OrganizationAlreadyExistsException(
                        "An organization with this tax ID already exists."
                );
            }


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
                .subscriptionTier(emptyToNull(request.getSubscriptionTier()))
                .billingEmail(emptyToNull(request.getBillingEmail()))
                .subscriptionStatus("ACTIVE")
                .enabled(true)
                .deleted(false)
                .build();

        // Save the organization
        Organization savedOrganization = organizationRepository.save(organization);

        // Create the organization's limits row: entity defaults apply, request caps override
        OrganizationLimit limit = OrganizationLimit.builder()
                .organization(savedOrganization)
                .build();
        applyQuotaCaps(limit, request.getUserQuota());
        OrganizationLimit savedLimit = organizationLimitRepository.save(limit);
        log.info("Successfully created organization with ID: {}", savedOrganization.getId());

        return OrganizationResponse.fromEntity(savedOrganization, savedLimit);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request, User currentUser) {
        log.info("Updating organization with ID: {} by user: {}", id, currentUser.getUsername());

        Organization organization = organizationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "The organization you requested does not exist."
                ));

        OrganizationLimit limit = getOrCreateLimit(organization);

        validateUpdateAuthorization(currentUser, id);
        validateOrganizerNameUniqueness(request.getOrganizerName(), organization.getOrganizerName());
        validateEmailUniqueness(request.getEmail(), organization.getEmail());
        validatePhoneNumberUniqueness(request.getPhoneNumber(), organization.getPhoneNumber());
        validateTaxIdUniqueness(request.getTaxId(), organization.getTaxId());
        validateUserLimits(limit, request);
        applyOrganizationUpdates(organization, request);
        applyQuotaCaps(limit, request.getUserQuota());

        Organization updatedOrganization = organizationRepository.save(organization);
        OrganizationLimit updatedLimit = organizationLimitRepository.save(limit);
        log.info("Successfully updated organization with ID: {}", updatedOrganization.getId());

        return OrganizationResponse.fromEntity(updatedOrganization, updatedLimit);
    }

    @Auditable(entityType = AuditEntityType.ORGANIZATION, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public OrganizationResponse toggleOrganizationStatus(Long id, Boolean enabled) {
        log.info("Toggling organization status for ID: {} to enabled={}", id, enabled);

        Organization organization = organizationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "The organization you requested does not exist."
                ));

        // Update organization's enabled status
        organization.setEnabled(enabled);
        Organization updatedOrganization = organizationRepository.save(organization);

        // Cascading behavior: Only disable users when organization is being disabled
        if (Boolean.FALSE.equals(enabled)) {
            // Disable all users in this organization
            List<User> organizationUsers = userRepository.findByOrganizationId(id);

            if (!organizationUsers.isEmpty()) {
                log.info("Disabling {} users for organization ID: {}", organizationUsers.size(), id);

                organizationUsers.forEach(user -> {
                    user.setEnabled(false);
                    log.debug("Disabling user: {} (ID: {})", user.getUsername(), user.getId());
                });

                userRepository.saveAll(organizationUsers);
                log.info("Successfully disabled all users for organization ID: {}", id);
            } else {
                log.info("No users found for organization ID: {}", id);
            }
        } else {
            // When enabling organization, do NOT automatically enable users
            log.info("Organization ID: {} enabled. Users remain in their current state (not automatically enabled).", id);
        }

        log.info("Successfully {} organization with ID: {}",
                Boolean.TRUE.equals(enabled) ? "enabled" : "disabled", updatedOrganization.getId());

        return toResponse(updatedOrganization);
    }

    private void validateUpdateAuthorization(User currentUser, Long organizationId) {
        if (currentUser.getRole() == UserRole.ORGANIZER_ADMIN && (currentUser.getOrganization() == null ||
                    !currentUser.getOrganization().getId().equals(organizationId))) {
                throw new UnauthorizedAccessException(
                        "You can only update your own organization.");
            }

    }

    private void validateEmailUniqueness(String newEmail, String currentEmail) {
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(currentEmail) && organizationRepository.existsByEmailAndDeletedFalse(newEmail)) {
                throw new OrganizationAlreadyExistsException(
                        "An organization with this email already exists."
                );
            }

    }

    private void validateTaxIdUniqueness(String newTaxId, String currentTaxId) {
        if (newTaxId != null && !newTaxId.isBlank() && !newTaxId.equals(currentTaxId) && organizationRepository.existsByTaxIdAndDeletedFalse(newTaxId)) {
                throw new OrganizationAlreadyExistsException(
                        "An organization with this tax ID already exists."
                );
            }

    }

    private void validateOrganizerNameUniqueness(String newName, String currentName) {
        if (newName != null && !newName.isBlank() && !newName.equals(currentName) && organizationRepository.existsByOrganizerNameAndDeletedFalse(newName)) {
            throw new OrganizationAlreadyExistsException(
                    "An organization with this name already exists."
            );
        }
    }

    private void validatePhoneNumberUniqueness(String newPhone, String currentPhone) {
        if (newPhone != null && !newPhone.isBlank() && !newPhone.equals(currentPhone) && organizationRepository.existsByPhoneNumberAndDeletedFalse(newPhone)) {
            throw new OrganizationAlreadyExistsException(
                    "An organization with this phone number already exists."
            );
        }
    }

    private void validateUserLimits(OrganizationLimit limit, UpdateOrganizationRequest request) {
        UserQuotaRequest quota = request.getUserQuota();
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

    /**
     * Rejects a cap change that would drop below the slots already in use.
     * A null new limit leaves the cap unchanged.
     */
    private void rejectIfBelowUsage(Integer newLimit, Integer used, String messageTemplate) {
        int inUse = used != null ? used : 0;
        if (newLimit != null && newLimit < inUse) {
            throw new UserLimitReductionException(String.format(messageTemplate, inUse));
        }
    }

    private void applyOrganizationUpdates(Organization organization, UpdateOrganizationRequest request) {
        updateBasicInfo(organization, request);
        updateContactInfo(organization, request);
        updateAddressInfo(organization, request);
        updateBusinessInfo(organization, request);
        updateSubscriptionInfo(organization, request);
    }

    private void updateBasicInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getOrganizerName() != null) {
            organization.setOrganizerName(request.getOrganizerName());
        }
    }

    private void updateContactInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getEmail() != null) {
            organization.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            organization.setPhoneNumber(emptyToNull(request.getPhoneNumber()));
        }
        if (request.getWebsite() != null) {
            organization.setWebsite(emptyToNull(request.getWebsite()));
        }
    }

    private void updateAddressInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getAddressLine1() != null) {
            organization.setAddressLine1(request.getAddressLine1());
        }
        if (request.getAddressLine2() != null) {
            organization.setAddressLine2(request.getAddressLine2());
        }
        if (request.getCity() != null) {
            organization.setCity(request.getCity());
        }
        if (request.getStateProvince() != null) {
            organization.setStateProvince(request.getStateProvince());
        }
        if (request.getPostalCode() != null) {
            organization.setPostalCode(request.getPostalCode());
        }
        if (request.getCountry() != null) {
            organization.setCountry(request.getCountry());
        }
    }

    private void updateBusinessInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getTaxId() != null) {
            organization.setTaxId(emptyToNull(request.getTaxId()));
        }
        if (request.getRegistrationNumber() != null) {
            organization.setRegistrationNumber(emptyToNull(request.getRegistrationNumber()));
        }
    }

    private void updateSubscriptionInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getSubscriptionTier() != null) {
            organization.setSubscriptionTier(emptyToNull(request.getSubscriptionTier()));
        }
        if (request.getBillingEmail() != null) {
            organization.setBillingEmail(emptyToNull(request.getBillingEmail()));
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id, User currentUser) {
        log.info("Fetching organization by ID: {} for user: {}", id, currentUser.getUsername());

        Organization organization = organizationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "The organization you requested does not exist."
                ));

        if ((currentUser.getRole() != UserRole.ROOT && currentUser.getRole() != UserRole.ADMIN) &&
                (currentUser.getOrganization() == null || !currentUser.getOrganization().getId().equals(id))) {
            throw new UnauthorizedAccessException(
                    "You can only view your own organization.");
        }

        log.info("Successfully retrieved organization with ID: {} for user: {}",
                id, currentUser.getUsername());

        return toResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentUserOrganization(User currentUser) {
        log.info("Fetching organization for user: {}", currentUser.getUsername());

        if (currentUser.getOrganization() == null) {
            throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
        }

        Long organizationId = currentUser.getOrganization().getId();

        Organization organization = organizationRepository.findByIdAndDeletedFalse(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "The organization you requested does not exist."));

        log.info("Successfully retrieved organization with ID: {} for user: {}",
                organizationId, currentUser.getUsername());

        return toResponse(organization);
    }

    /**
     * Builds a response for a single organization, loading its limits row.
     */
    private OrganizationResponse toResponse(Organization organization) {
        OrganizationLimit limit = organizationLimitRepository.findById(organization.getId()).orElse(null);
        return OrganizationResponse.fromEntity(organization, limit);
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

    private void applyCap(UserQuotaRequest.RoleQuotaRequest role, Consumer<Integer> setter) {
        if (role != null && role.getMax() != null) {
            setter.accept(role.getMax());
        }
    }

    private Integer requestedMax(UserQuotaRequest.RoleQuotaRequest role) {
        return role != null ? role.getMax() : null;
    }
}
