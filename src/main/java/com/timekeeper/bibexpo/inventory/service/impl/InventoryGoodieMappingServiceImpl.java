package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotMappableException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryGoodieMappingService;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryGoodieMappingServiceImpl implements InventoryGoodieMappingService {

    private final InventoryGoodieMappingRepository mappingRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final InventoryAccessGuard accessGuard;
    private final EventStore eventStore;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryGoodieMappingResponse> listMappings(Long organizationId, Long eventId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireEventInOrg(eventId, organizationId);

        List<InventoryGoodieMapping> mappings = mappingRepository.findByEventIdOrderByGoodieNameAsc(eventId);
        if (mappings.isEmpty()) {
            return List.of();
        }

        Map<Long, String> itemNames = itemRepository.findAllById(
                        mappings.stream().map(InventoryGoodieMapping::getItemId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));

        return mappings.stream()
                .map(mapping -> InventoryGoodieMappingResponse.of(mapping, itemNames.get(mapping.getItemId())))
                .toList();
    }

    @Override
    @Transactional
    public InventoryGoodieMappingResponse createMapping(Long organizationId, Long eventId,
                                                        CreateInventoryGoodieMappingRequest request,
                                                        User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireEventInOrg(eventId, organizationId);

        InventoryItem item = requireMappableItem(request.getItemId(), organizationId);

        String goodieName = request.getGoodieName().trim();
        if (mappingRepository.existsByEventIdAndGoodieName(eventId, goodieName)) {
            throw new InventoryGoodieMappingAlreadyExistsException();
        }

        InventoryGoodieMapping mapping = mappingRepository.save(InventoryGoodieMapping.builder()
                .organizationId(organizationId)
                .eventId(eventId)
                .goodieName(goodieName)
                .itemId(item.getId())
                .build());

        log.info("Linked goody '{}' of event {} to inventory item {}", goodieName, eventId, item.getId());
        return InventoryGoodieMappingResponse.of(mapping, item.getName());
    }

    @Override
    @Transactional
    public InventoryGoodieMappingResponse updateMapping(Long organizationId, Long eventId, Long mappingId,
                                                        UpdateInventoryGoodieMappingRequest request,
                                                        User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireEventInOrg(eventId, organizationId);

        InventoryGoodieMapping mapping = requireMapping(mappingId, eventId, organizationId);
        InventoryItem item = requireMappableItem(request.getItemId(), organizationId);

        mapping.setItemId(item.getId());
        InventoryGoodieMapping saved = mappingRepository.saveAndFlush(mapping);

        log.info("Goody '{}' of event {} now points at inventory item {}",
                saved.getGoodieName(), eventId, item.getId());
        return InventoryGoodieMappingResponse.of(saved, item.getName());
    }

    @Override
    @Transactional
    public void deleteMapping(Long organizationId, Long eventId, Long mappingId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireEventInOrg(eventId, organizationId);

        InventoryGoodieMapping mapping = requireMapping(mappingId, eventId, organizationId);
        mappingRepository.delete(mapping);
        log.info("Unlinked goody '{}' of event {}", mapping.getGoodieName(), eventId);
    }

    // ---- lookups ----------------------------------------------------------------

    /**
     * An event another organization owns is reported as missing rather than forbidden, so a caller
     * learns nothing about events outside their own organization.
     */
    private void requireEventInOrg(Long eventId, Long organizationId) {
        Event event = eventStore.requireById(eventId);
        if (event.getOrganization() == null || !event.getOrganization().getId().equals(organizationId)) {
            throw new EventNotFoundException();
        }
    }

    private InventoryGoodieMapping requireMapping(Long mappingId, Long eventId, Long organizationId) {
        return mappingRepository.findByIdAndEventIdAndOrganizationId(mappingId, eventId, organizationId)
                .orElseThrow(InventoryGoodieMappingNotFoundException::new);
    }

    private InventoryItem requireMappableItem(Long itemId, Long organizationId) {
        InventoryItem item = itemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(InventoryItemNotFoundException::new);

        if (variantAttributeValueRepository.findDistinctAttributeIdsByItemId(itemId).size() > 1) {
            log.info("Item {} varies by more than one attribute and cannot back a goody", itemId);
            throw new InventoryItemNotMappableException();
        }
        return item;
    }
}
