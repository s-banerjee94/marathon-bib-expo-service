package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        log.info("Creating organization with email: {}", request.getEmail());

        // Check if organization with email already exists
        if (organizationRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new OrganizationAlreadyExistsException(
                    "Organization with email '" + request.getEmail() + "' already exists"
            );
        }

        // Check if organization with taxId already exists (if taxId is provided)
        if (request.getTaxId() != null && !request.getTaxId().isBlank() && organizationRepository.existsByTaxIdAndDeletedFalse(request.getTaxId())) {
                throw new OrganizationAlreadyExistsException(
                        "Organization with tax ID '" + request.getTaxId() + "' already exists"
                );
            }


        // Build the organization entity
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
                .maxOrganizerUsers(request.getMaxOrganizerUsers() != null ?
                        request.getMaxOrganizerUsers() : 5)
                .maxDistributors(request.getMaxDistributors() != null ?
                        request.getMaxDistributors() : 30)
                .subscriptionTier(request.getSubscriptionTier())
                .billingEmail(request.getBillingEmail())
                .subscriptionStatus("ACTIVE")
                .enabled(true)
                .deleted(false)
                .build();

        // Save the organization
        Organization savedOrganization = organizationRepository.save(organization);
        log.info("Successfully created organization with ID: {}", savedOrganization.getId());

        return OrganizationResponse.fromEntity(savedOrganization);
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request, User currentUser) {
        log.info("Updating organization with ID: {} by user: {}", id, currentUser.getUsername());

        Organization organization = organizationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "Organization with ID '" + id + "' not found or has been deleted"
                ));

        validateUpdateAuthorization(currentUser, id);
        validateEmailUniqueness(request.getEmail(), organization.getEmail());
        validateTaxIdUniqueness(request.getTaxId(), organization.getTaxId());
        applyOrganizationUpdates(organization, request);

        Organization updatedOrganization = organizationRepository.save(organization);
        log.info("Successfully updated organization with ID: {}", updatedOrganization.getId());

        return OrganizationResponse.fromEntity(updatedOrganization);
    }

    @Override
    @Transactional
    public OrganizationResponse toggleOrganizationStatus(Long id, Boolean enabled) {
        log.info("Toggling organization status for ID: {} to enabled={}", id, enabled);

        // Fetch the organization
        Organization organization = organizationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "Organization with ID '" + id + "' not found or has been deleted"
                ));

        // Update organization's enabled status
        organization.setEnabled(enabled);
        Organization updatedOrganization = organizationRepository.save(organization);

        // Cascading behavior: Only disable users when organization is being disabled
        if (Boolean.FALSE.equals(enabled)) {
            // Disable all users in this organization (excluding deleted users)
            List<User> organizationUsers = userRepository.findByOrganizationIdAndDeletedFalse(id);

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

        return OrganizationResponse.fromEntity(updatedOrganization);
    }

    private void validateUpdateAuthorization(User currentUser, Long organizationId) {
        if (currentUser.getRole() == UserRole.ORGANIZER_ADMIN && (currentUser.getOrganization() == null ||
                    !currentUser.getOrganization().getId().equals(organizationId))) {
                throw new UnauthorizedAccessException(
                        "You can only update your own organization");
            }

    }

    private void validateEmailUniqueness(String newEmail, String currentEmail) {
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(currentEmail) && organizationRepository.existsByEmailAndDeletedFalse(newEmail)) {
                throw new OrganizationAlreadyExistsException(
                        "Organization with email '" + newEmail + "' already exists"
                );
            }

    }

    private void validateTaxIdUniqueness(String newTaxId, String currentTaxId) {
        if (newTaxId != null && !newTaxId.isBlank() && !newTaxId.equals(currentTaxId) && organizationRepository.existsByTaxIdAndDeletedFalse(newTaxId)) {
                throw new OrganizationAlreadyExistsException(
                        "Organization with tax ID '" + newTaxId + "' already exists"
                );
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
            organization.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getWebsite() != null) {
            organization.setWebsite(request.getWebsite());
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
            organization.setTaxId(request.getTaxId());
        }
        if (request.getRegistrationNumber() != null) {
            organization.setRegistrationNumber(request.getRegistrationNumber());
        }
        if (request.getMaxOrganizerUsers() != null) {
            organization.setMaxOrganizerUsers(request.getMaxOrganizerUsers());
        }
        if (request.getMaxDistributors() != null) {
            organization.setMaxDistributors(request.getMaxDistributors());
        }
    }

    private void updateSubscriptionInfo(Organization organization, UpdateOrganizationRequest request) {
        if (request.getSubscriptionTier() != null) {
            organization.setSubscriptionTier(request.getSubscriptionTier());
        }
        if (request.getBillingEmail() != null) {
            organization.setBillingEmail(request.getBillingEmail());
        }
    }
}
