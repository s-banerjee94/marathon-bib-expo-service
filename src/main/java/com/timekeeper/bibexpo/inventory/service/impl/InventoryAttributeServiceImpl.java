package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.enums.AttributeType;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeOptionRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryAttributeService;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAttributeServiceImpl implements InventoryAttributeService {

    private final InventoryAttributeRepository attributeRepository;
    private final InventoryAttributeOptionRepository optionRepository;
    private final InventoryItemAttributeValueRepository itemAttributeValueRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final OrganizationDirectory organizationDirectory;
    private final InventoryAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public InventoryAttributeListResponse listVisible(Long organizationId, User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
            return toScopedResponse(attributeRepository.findVisible(organizationId), false);
        }
        return toScopedResponse(attributeRepository.findByOrganizationIdIsNullOrderByName(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryAttributeResponse getAttribute(Long organizationId, Long attributeId, User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        return toResponse(requireAttributeInScope(attributeId, organizationId));
    }

    @Override
    @Transactional
    public InventoryAttributeResponse createAttribute(Long organizationId,
                                                                   CreateInventoryAttributeRequest request,
                                                                   User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }

        // Only a fixed choice list keeps variant keys deduplicated; free text would let "Red" and
        // "red" become two stock lines for one colour.
        if (Boolean.TRUE.equals(request.getVariantAttribute()) && request.getType() != AttributeType.SELECT) {
            throw new InvalidUserDataException("Only a SELECT attribute can define variants.");
        }

        String name = request.getName().trim();
        if (existsByName(organizationId, name)) {
            throw new InventoryAttributeAlreadyExistsException();
        }

        InventoryAttribute attribute = attributeRepository.save(InventoryAttribute.builder()
                .organizationId(organizationId)
                .name(name)
                .type(request.getType())
                .variantAttribute(request.getVariantAttribute())
                .required(request.getRequired())
                .build());

        log.info("Created inventory attribute '{}' ({}) for organization {}", name, request.getType(), organizationId);
        return InventoryAttributeResponse.fromEntity(attribute, List.of());
    }

    @Override
    @Transactional
    public InventoryAttributeResponse updateAttribute(Long organizationId, Long attributeId,
                                                                   UpdateInventoryAttributeRequest request,
                                                                   User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        InventoryAttribute attribute = requireAttributeInScope(attributeId, organizationId);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.equalsIgnoreCase(attribute.getName()) && existsByName(organizationId, name)) {
                throw new InventoryAttributeAlreadyExistsException();
            }
            attribute.setName(name);
        }
        if (request.getRequired() != null) {
            attribute.setRequired(request.getRequired());
        }

        InventoryAttribute saved = attributeRepository.saveAndFlush(attribute);
        log.info("Updated inventory attribute {} for organization {}", attributeId, organizationId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteAttribute(Long organizationId, Long attributeId, User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        InventoryAttribute attribute = requireAttributeInScope(attributeId, organizationId);

        boolean inUse = itemAttributeValueRepository.countByAttributeId(attributeId) > 0
                || variantAttributeValueRepository.countByAttributeId(attributeId) > 0;
        if (inUse) {
            throw new InventoryAttributeInUseException();
        }

        optionRepository.deleteAll(optionRepository.findByAttributeId(attributeId));
        attributeRepository.delete(attribute);
        log.info("Deleted inventory attribute {} for organization {}", attributeId, organizationId);
    }

    @Override
    @Transactional
    public InventoryAttributeResponse addOption(Long organizationId, Long attributeId,
                                                           CreateInventoryAttributeOptionRequest request,
                                                           User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        InventoryAttribute attribute = requireAttributeInScope(attributeId, organizationId);
        requireSelectType(attribute);
        if (organizationId != null) {
            enforceOptionLimit(organizationId, attributeId);
        }

        String value = request.getValue().trim();
        if (optionRepository.existsByAttributeIdAndValue(attributeId, value)) {
            throw new InventoryAttributeOptionAlreadyExistsException();
        }
        optionRepository.save(InventoryAttributeOption.builder()
                .attributeId(attributeId)
                .value(value)
                .build());

        log.info("Added value '{}' to inventory attribute {} for organization {}", value, attributeId, organizationId);
        return toResponse(attribute);
    }

    @Override
    @Transactional
    public InventoryAttributeResponse updateOption(Long organizationId, Long attributeId, Long optionId,
                                                              UpdateInventoryAttributeOptionRequest request,
                                                              User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        InventoryAttribute attribute = requireAttributeInScope(attributeId, organizationId);
        InventoryAttributeOption option = requireOptionOfAttribute(optionId, attributeId);

        String value = request.getValue().trim();
        if (!value.equalsIgnoreCase(option.getValue())
                && optionRepository.existsByAttributeIdAndValue(attributeId, value)) {
            throw new InventoryAttributeOptionAlreadyExistsException();
        }
        option.setValue(value);
        optionRepository.saveAndFlush(option);

        log.info("Renamed value {} to '{}' on inventory attribute {} for organization {}",
                optionId, value, attributeId, organizationId);
        return toResponse(attribute);
    }

    @Override
    @Transactional
    public InventoryAttributeResponse removeOption(Long organizationId, Long attributeId, Long optionId,
                                                              User currentUser) {
        if (organizationId != null) {
            accessGuard.requireOrgAccess(currentUser, organizationId);
        }
        InventoryAttribute attribute = requireAttributeInScope(attributeId, organizationId);
        InventoryAttributeOption option = requireOptionOfAttribute(optionId, attributeId);

        boolean inUse = itemAttributeValueRepository.countByOptionId(optionId) > 0
                || variantAttributeValueRepository.countByOptionId(optionId) > 0;
        if (inUse) {
            throw new InventoryAttributeOptionInUseException();
        }

        optionRepository.delete(option);
        log.info("Removed value {} from inventory attribute {} for organization {}", optionId, attributeId, organizationId);
        return toResponse(attribute);
    }

    // ---- lookups ----------------------------------------------------------------

    // An attribute outside the caller's scope reads as not found, so a platform default cannot be
    // edited through the organization path, nor an organization's own attribute through the platform one.
    private InventoryAttribute requireAttributeInScope(Long attributeId, Long organizationId) {
        InventoryAttribute attribute = attributeRepository.findById(attributeId)
                .orElseThrow(InventoryAttributeNotFoundException::new);
        if (!Objects.equals(attribute.getOrganizationId(), organizationId)) {
            throw new InventoryAttributeNotFoundException();
        }
        return attribute;
    }

    private InventoryAttributeOption requireOptionOfAttribute(Long optionId, Long attributeId) {
        InventoryAttributeOption value = optionRepository.findById(optionId)
                .orElseThrow(InventoryAttributeOptionNotFoundException::new);
        if (!value.getAttributeId().equals(attributeId)) {
            throw new InventoryAttributeOptionNotFoundException();
        }
        return value;
    }

    private void requireSelectType(InventoryAttribute attribute) {
        if (attribute.getType() != AttributeType.SELECT) {
            throw new InvalidUserDataException("Only a SELECT attribute can have a list of allowed values.");
        }
    }

    // Check-then-act like the term cap: a double-click racing past it by one costs nothing worth
    // a counter column. Platform defaults belong to no organization and stay uncapped.
    private void enforceOptionLimit(Long organizationId, Long attributeId) {
        if (optionRepository.countByAttributeId(attributeId)
                >= organizationDirectory.inventoryLimits(organizationId).maxOptionsPerAttribute()) {
            log.error("Organization {} has reached its value limit on inventory attribute {}",
                    organizationId, attributeId);
            throw new InventoryAttributeOptionLimitReachedException();
        }
    }

    private boolean existsByName(Long organizationId, String name) {
        return organizationId != null
                ? attributeRepository.existsByOrganizationIdAndName(organizationId, name)
                : attributeRepository.existsByOrganizationIdIsNullAndName(name);
    }

    private InventoryAttributeResponse toResponse(InventoryAttribute attribute) {
        return InventoryAttributeResponse.fromEntity(attribute,
                optionRepository.findByAttributeId(attribute.getId()));
    }

    // One options query for both groups: batch first, split the responses afterwards. A platform
    // default's audit trail is shown only on the platform's own path; an organization browsing the
    // shared attributes has no claim on who last changed one it does not own.
    private InventoryAttributeListResponse toScopedResponse(List<InventoryAttribute> attributes,
                                                            boolean showPlatformAudit) {
        Map<Long, List<InventoryAttributeOption>> optionsByAttribute = attributes.isEmpty() ? Map.of()
                : optionRepository
                        .findByAttributeIdIn(attributes.stream().map(InventoryAttribute::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(InventoryAttributeOption::getAttributeId));
        return InventoryAttributeListResponse.builder()
                .platformDefaults(attributes.stream()
                        .filter(a -> a.getOrganizationId() == null)
                        .map(a -> InventoryAttributeResponse.fromEntity(a,
                                optionsByAttribute.getOrDefault(a.getId(), List.of()), showPlatformAudit))
                        .toList())
                .organizationAttributes(attributes.stream()
                        .filter(a -> a.getOrganizationId() != null)
                        .map(a -> InventoryAttributeResponse.fromEntity(a,
                                optionsByAttribute.getOrDefault(a.getId(), List.of())))
                        .toList())
                .build();
    }
}
