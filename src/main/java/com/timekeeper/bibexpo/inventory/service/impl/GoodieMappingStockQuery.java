package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.api.GoodieIssue;
import com.timekeeper.bibexpo.inventory.api.GoodieStockOption;
import com.timekeeper.bibexpo.inventory.api.GoodieStockQuery;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.service.util.VariantLabeller;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Answers the counter from the event's goody links: which goodies come out of inventory, and which
 * variant a hand-over takes.
 */
@Component
@RequiredArgsConstructor
public class GoodieMappingStockQuery implements GoodieStockQuery {

    private final InventoryGoodieMappingRepository mappingRepository;
    private final InventoryItemRepository itemRepository;
    private final VariantLabeller variantLabeller;

    @Override
    @Transactional(readOnly = true)
    public List<GoodieStockOption> optionsFor(Long eventId) {
        List<InventoryGoodieMapping> links = mappingRepository.findByEventIdOrderByGoodieNameAsc(eventId);
        if (links.isEmpty()) {
            return List.of();
        }

        Map<Long, String> itemNames = itemRepository.findAllById(
                        links.stream().map(InventoryGoodieMapping::getItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));
        // Two goodies can come out of one item, so each item's variants are labelled once.
        Map<Long, List<GoodieStockOption.Variant>> variantsByItem = new HashMap<>();
        return links.stream()
                .map(link -> new GoodieStockOption(link.getGoodieName(), itemNames.get(link.getItemId()),
                        variantsByItem.computeIfAbsent(link.getItemId(), this::variantsOf)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodieIssue> planIssue(Long eventId, String goodieName, Long variantId) {
        return mappingRepository.findByEventIdAndGoodieName(eventId, goodieName)
                .map(link -> plan(link, goodieName, variantId));
    }

    private GoodieIssue plan(InventoryGoodieMapping link, String goodieName, Long variantId) {
        Map<Long, String> labels = variantLabeller.labelsForItem(link.getItemId());
        Long chosen = variantId;
        if (chosen == null) {
            if (labels.size() != 1) {
                throw new InvalidUserDataException(
                        "Please choose which variant of " + goodieName + " you are handing over.");
            }
            chosen = labels.keySet().iterator().next();
        }
        if (!labels.containsKey(chosen)) {
            throw new InvalidUserDataException("The variant you chose does not belong to " + goodieName + ".");
        }
        return new GoodieIssue(chosen, labels.get(chosen), link.getLocationId());
    }

    private List<GoodieStockOption.Variant> variantsOf(Long itemId) {
        return variantLabeller.labelsForItem(itemId).entrySet().stream()
                .map(label -> new GoodieStockOption.Variant(label.getKey(), label.getValue()))
                .toList();
    }
}
