package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.api.GoodieEntitlement;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingLocationRequiredException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotMappableException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieResolutionResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieShortfallResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAlias;
import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryStockRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAliasRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryGoodieMappingService;
import com.timekeeper.bibexpo.inventory.service.util.VariantLabeller;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryGoodieMappingServiceImpl implements InventoryGoodieMappingService {

    private final InventoryGoodieMappingRepository mappingRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryStockRepository stockRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final InventoryVariantAliasRepository aliasRepository;
    private final InventoryAccessGuard accessGuard;
    private final VariantLabeller variantLabeller;
    private final EventStore eventStore;
    private final EventStatsQuery eventStatsQuery;

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
        Map<Long, String> locationNames = locationRepository.findAllById(
                        mappings.stream().map(InventoryGoodieMapping::getLocationId)
                                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));

        return mappings.stream()
                .map(mapping -> InventoryGoodieMappingResponse.of(mapping, itemNames.get(mapping.getItemId()),
                        locationNames.get(mapping.getLocationId())))
                .toList();
    }

    @Override
    @Transactional
    public InventoryGoodieMappingResponse createMapping(Long organizationId, Long eventId,
                                                        CreateInventoryGoodieMappingRequest request,
                                                        User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        Event event = requireEventInOrg(eventId, organizationId);

        InventoryItem item = requireMappableItem(request.getItemId(), organizationId);
        InventoryLocation location = resolveLocation(request.getLocationId(), organizationId, event);

        String goodieName = request.getGoodieName().trim();
        if (mappingRepository.existsByEventIdAndGoodieName(eventId, goodieName)) {
            throw new InventoryGoodieMappingAlreadyExistsException();
        }

        InventoryGoodieMapping mapping = mappingRepository.save(InventoryGoodieMapping.builder()
                .organizationId(organizationId)
                .eventId(eventId)
                .goodieName(goodieName)
                .itemId(item.getId())
                .locationId(location == null ? null : location.getId())
                .build());

        log.info("Linked goody '{}' of event {} to inventory item {} at location {}",
                goodieName, eventId, item.getId(), mapping.getLocationId());
        return InventoryGoodieMappingResponse.of(mapping, item.getName(),
                location == null ? null : location.getName());
    }

    @Override
    @Transactional
    public InventoryGoodieMappingResponse updateMapping(Long organizationId, Long eventId, Long mappingId,
                                                        UpdateInventoryGoodieMappingRequest request,
                                                        User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        Event event = requireEventInOrg(eventId, organizationId);

        InventoryGoodieMapping mapping = requireMapping(mappingId, eventId, organizationId);
        InventoryItem item = requireMappableItem(request.getItemId(), organizationId);
        InventoryLocation location = resolveLocation(request.getLocationId(), organizationId, event);

        mapping.setItemId(item.getId());
        mapping.setLocationId(location == null ? null : location.getId());
        InventoryGoodieMapping saved = mappingRepository.saveAndFlush(mapping);

        log.info("Goody '{}' of event {} now points at inventory item {} at location {}",
                saved.getGoodieName(), eventId, item.getId(), saved.getLocationId());
        return InventoryGoodieMappingResponse.of(saved, item.getName(),
                location == null ? null : location.getName());
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

    @Override
    @Transactional(readOnly = true)
    public List<InventoryGoodieResolutionResponse> resolveGoodies(Long organizationId, Long eventId,
                                                                  User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireEventInOrg(eventId, organizationId);

        Map<String, InventoryGoodieMapping> links = mappingRepository.findByEventIdOrderByGoodieNameAsc(eventId)
                .stream()
                .collect(Collectors.toMap(link -> normalize(link.getGoodieName()), link -> link,
                        (first, second) -> first, LinkedHashMap::new));

        Map<String, List<GoodieEntitlement>> demand = new LinkedHashMap<>();
        Map<String, String> goodieNames = new LinkedHashMap<>();
        for (GoodieEntitlement entitlement : eventStatsQuery.entitlements(eventId)) {
            String key = normalize(entitlement.goodieName());
            demand.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entitlement);
            goodieNames.putIfAbsent(key, entitlement.goodieName());
        }
        // A goody linked but never imported still belongs on the screen — it is the clearest sign
        // the heading was typed differently from the column.
        links.forEach((key, link) -> goodieNames.putIfAbsent(key, link.getGoodieName()));

        Map<Long, String> itemNames = itemRepository.findAllById(
                        links.values().stream().map(InventoryGoodieMapping::getItemId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));

        return goodieNames.entrySet().stream()
                .map(entry -> resolveGoodie(entry.getValue(), links.get(entry.getKey()),
                        demand.getOrDefault(entry.getKey(), List.of()), itemNames))
                .sorted(Comparator.comparing(InventoryGoodieResolutionResponse::getGoodieName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryGoodieShortfallResponse> shortfall(Long organizationId, Long eventId,
                                                            User currentUser) {
        List<InventoryGoodieResolutionResponse> resolved =
                resolveGoodies(organizationId, eventId, currentUser);
        if (resolved.isEmpty()) {
            return List.of();
        }

        Map<Long, InventoryGoodieMapping> links = mappingRepository.findByEventIdOrderByGoodieNameAsc(eventId)
                .stream().collect(Collectors.toMap(InventoryGoodieMapping::getId, link -> link));
        List<Long> wanted = resolved.stream()
                .flatMap(goody -> goody.getValues().stream())
                .map(InventoryGoodieResolutionResponse.Value::getVariantId)
                .filter(Objects::nonNull).distinct().toList();
        // One balance read covers every location, which is what lets a shortfall say whether the
        // units are already somewhere else in the organization or have to be bought.
        Map<Long, List<InventoryStock>> balances = wanted.isEmpty() ? Map.of()
                : stockRepository.findByVariantIdIn(wanted).stream()
                .collect(Collectors.groupingBy(InventoryStock::getVariantId));
        Map<Long, String> locationNames = locationRepository.findAllById(
                        links.values().stream().map(InventoryGoodieMapping::getLocationId)
                                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));

        return resolved.stream()
                .map(goody -> shortfallFor(goody, links.get(goody.getMappingId()), balances, locationNames))
                .toList();
    }

    private InventoryGoodieShortfallResponse shortfallFor(InventoryGoodieResolutionResponse goody,
                                                          InventoryGoodieMapping link,
                                                          Map<Long, List<InventoryStock>> balances,
                                                          Map<Long, String> locationNames) {
        Long locationId = link == null ? null : link.getLocationId();
        InventoryGoodieShortfallResponse.InventoryGoodieShortfallResponseBuilder response =
                InventoryGoodieShortfallResponse.builder()
                        .goodieName(goody.getGoodieName())
                        .mappingId(goody.getMappingId())
                        .itemId(goody.getItemId())
                        .itemName(goody.getItemName())
                        .locationId(locationId)
                        .locationName(locationId == null ? null : locationNames.get(locationId))
                        .participants(goody.getParticipants())
                        .unresolvedParticipants(goody.getUnresolvedParticipants())
                        .countedByValue(goody.isCountedByValue());

        // A column with too many distinct values was never counted one by one, so there is no
        // demand to total and nothing honest to say about a shortfall.
        if (!goody.isCountedByValue()) {
            return response.variants(List.of()).build();
        }

        // Several spellings routinely mean one variant -- M, Medium and 38 are one shelf.
        Map<Long, Long> demand = new LinkedHashMap<>();
        Map<Long, String> variantLabels = new LinkedHashMap<>();
        for (InventoryGoodieResolutionResponse.Value value : goody.getValues()) {
            Long variantId = value.getVariantId();
            if (variantId == null) {
                continue;
            }
            demand.merge(variantId, value.getParticipants(), Long::sum);
            variantLabels.putIfAbsent(variantId, value.getVariantLabel());
        }

        List<InventoryGoodieShortfallResponse.Variant> variants = new ArrayList<>(demand.size());
        long needed = 0;
        long onHand = 0;
        long shortfall = 0;
        long elsewhere = 0;
        for (Map.Entry<Long, Long> entry : demand.entrySet()) {
            Long variantId = entry.getKey();
            long variantNeeded = entry.getValue();
            long variantOnHand = 0;
            long variantElsewhere = 0;
            for (InventoryStock balance : balances.getOrDefault(variantId, List.of())) {
                long held = balance.getOnHand() == null ? 0 : balance.getOnHand();
                if (locationId != null && locationId.equals(balance.getLocationId())) {
                    variantOnHand += held;
                } else {
                    variantElsewhere += held;
                }
            }
            long variantShortfall = Math.max(0, variantNeeded - variantOnHand);

            needed += variantNeeded;
            onHand += variantOnHand;
            shortfall += variantShortfall;
            elsewhere += variantElsewhere;
            variants.add(InventoryGoodieShortfallResponse.Variant.builder()
                    .variantId(variantId)
                    .variantLabel(variantLabels.get(variantId))
                    .needed(variantNeeded)
                    .onHand(variantOnHand)
                    .shortfall(variantShortfall)
                    .elsewhere(variantElsewhere)
                    .build());
        }
        variants.sort(Comparator.comparingLong(InventoryGoodieShortfallResponse.Variant::getShortfall)
                .thenComparing(InventoryGoodieShortfallResponse.Variant::getNeeded).reversed());

        return response.needed(needed).onHand(onHand).shortfall(shortfall).elsewhere(elsewhere)
                .variants(variants).build();
    }

    // ---- the check screen -------------------------------------------------------

    private InventoryGoodieResolutionResponse resolveGoodie(String goodieName, InventoryGoodieMapping link,
                                                            List<GoodieEntitlement> demand,
                                                            Map<Long, String> itemNames) {
        InventoryGoodieResolutionResponse.InventoryGoodieResolutionResponseBuilder response =
                InventoryGoodieResolutionResponse.builder()
                        .goodieName(goodieName)
                        .mappingId(link == null ? null : link.getId())
                        .itemId(link == null ? null : link.getItemId())
                        .itemName(link == null ? null : itemNames.get(link.getItemId()));

        GoodieEntitlement uncounted = demand.stream().filter(row -> !row.countedByValue()).findFirst().orElse(null);
        if (uncounted != null) {
            return response.participants(uncounted.participants()).countedByValue(false)
                    .values(List.of()).build();
        }

        Map<Long, String> labels = link == null ? Map.of() : variantLabeller.labelsForItem(link.getItemId());
        Map<String, Long> ownValues = labels.entrySet().stream()
                .filter(label -> !label.getValue().isEmpty())
                .collect(Collectors.toMap(label -> normalize(label.getValue()), Map.Entry::getKey,
                        (first, second) -> first));
        Map<String, InventoryVariantAlias> aliases = link == null ? Map.of()
                : aliasRepository.findByItemIdOrderBySourceValueAsc(link.getItemId()).stream()
                .collect(Collectors.toMap(alias -> normalize(alias.getSourceValue()), alias -> alias,
                        (first, second) -> first));
        // An item that varies by nothing has one unnamed variant, and every runner owed the goody
        // is owed that one. The spellings are still read first, so "Not mentioned" can be taught to mean
        // nothing is owed.
        Long handedOverAsIs = ownValues.isEmpty() && labels.size() == 1
                ? labels.keySet().iterator().next() : null;

        long participants = 0;
        long unresolved = 0;
        List<InventoryGoodieResolutionResponse.Value> values = new ArrayList<>(demand.size());
        for (GoodieEntitlement entitlement : demand) {
            String key = normalize(entitlement.value());
            GoodieValueResolution resolution;
            Long variantId = null;
            if (link == null) {
                resolution = GoodieValueResolution.NOT_LINKED;
            } else if (ownValues.containsKey(key)) {
                resolution = GoodieValueResolution.VARIANT;
                variantId = ownValues.get(key);
            } else if (aliases.containsKey(key)) {
                variantId = aliases.get(key).getVariantId();
                resolution = variantId == null
                        ? GoodieValueResolution.NOTHING_OWED : GoodieValueResolution.ALIAS;
            } else if (handedOverAsIs != null) {
                resolution = GoodieValueResolution.SINGLE_VARIANT;
                variantId = handedOverAsIs;
            } else {
                resolution = GoodieValueResolution.UNRESOLVED;
            }

            participants += entitlement.participants();
            if (resolution == GoodieValueResolution.UNRESOLVED || resolution == GoodieValueResolution.NOT_LINKED) {
                unresolved += entitlement.participants();
            }

            String label = variantId == null ? null : labels.get(variantId);
            values.add(InventoryGoodieResolutionResponse.Value.builder()
                    .value(entitlement.value())
                    .participants(entitlement.participants())
                    .resolution(resolution)
                    .variantId(variantId)
                    .variantLabel(label == null || label.isEmpty() ? null : label)
                    .build());
        }
        values.sort(Comparator.comparingLong(InventoryGoodieResolutionResponse.Value::getParticipants).reversed()
                .thenComparing(InventoryGoodieResolutionResponse.Value::getValue, String.CASE_INSENSITIVE_ORDER));

        return response.participants(participants).unresolvedParticipants(unresolved)
                .countedByValue(true).values(values).build();
    }

    /**
     * Headings and cell values are compared the way the rest of this feature stores them: trimmed,
     * and without regard to case, which is what the columns' own collation already does.
     */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    // ---- lookups ----------------------------------------------------------------

    /**
     * An event another organization owns is reported as missing rather than forbidden, so a caller
     * learns nothing about events outside their own organization.
     */
    private Event requireEventInOrg(Long eventId, Long organizationId) {
        Event event = eventStore.requireById(eventId);
        if (event.getOrganization() == null || !event.getOrganization().getId().equals(organizationId)) {
            throw new EventNotFoundException();
        }
        return event;
    }

    /**
     * A draft may be linked without a location and finish the decision later, which is the ordinary
     * order of events — the item is known when the roster lands, the counter often only days before
     * the expo. Once published that slack is gone, because a link with nowhere to deduct from would
     * hand goodies out against nothing.
     */
    private InventoryLocation resolveLocation(Long locationId, Long organizationId, Event event) {
        if (locationId == null) {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                throw new InventoryGoodieMappingLocationRequiredException();
            }
            return null;
        }
        return locationRepository.findByIdAndOrganizationId(locationId, organizationId)
                .orElseThrow(InventoryLocationNotFoundException::new);
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
