package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.api.GoodieIssue;
import com.timekeeper.bibexpo.inventory.api.GoodieRequest;
import com.timekeeper.bibexpo.inventory.api.GoodieStockOption;
import com.timekeeper.bibexpo.inventory.api.GoodieStockQuery;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.service.util.GoodieValueReader;
import com.timekeeper.bibexpo.inventory.service.util.VariantLabeller;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answers the counter from the event's goody links: which goodies come out of inventory, and which
 * variant a hand-over takes. A participant's own value is read exactly as the check screen reads it.
 */
@Component
@RequiredArgsConstructor
public class GoodieMappingStockQuery implements GoodieStockQuery {

    private final InventoryGoodieMappingRepository mappingRepository;
    private final InventoryItemRepository itemRepository;
    private final VariantLabeller variantLabeller;
    private final GoodieValueReader valueReader;

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

    // The counter's pick always wins, which is how a participant swaps sizes. Without one, their own value
    // names the variant, and an item with a single variant needs nobody to choose.
    @Override
    @Transactional(readOnly = true)
    public Map<String, GoodieIssue> planIssues(Long eventId, List<GoodieRequest> requests) {
        Map<String, InventoryGoodieMapping> links = linksByName(eventId);
        Map<Long, GoodieValueReader.ItemReader> readers = new HashMap<>();
        Map<String, GoodieIssue> issues = new HashMap<>();
        List<String> unchosen = new ArrayList<>();
        List<String> foreign = new ArrayList<>();
        for (GoodieRequest request : requests) {
            InventoryGoodieMapping link = links.get(TextUtils.toMatchKey(request.goodieName()));
            if (link == null) {
                continue;
            }
            GoodieValueReader.ItemReader reader = readers.computeIfAbsent(link.getItemId(), valueReader::forItem);
            Long chosen = request.variantId();
            if (chosen == null && request.value() != null) {
                GoodieValueReader.Reading reading = reader.read(request.value());
                if (reading.resolution() == GoodieValueResolution.NOTHING_OWED) {
                    continue;
                }
                chosen = reading.variantId();
            }
            Map<Long, String> labels = reader.labels();
            if (chosen == null && labels.size() == 1) {
                chosen = labels.keySet().iterator().next();
            }
            if (chosen == null) {
                unchosen.add(request.goodieName());
            } else if (!labels.containsKey(chosen)) {
                foreign.add(request.goodieName());
            } else {
                issues.put(request.goodieName(), new GoodieIssue(chosen, labels.get(chosen), link.getLocationId()));
            }
        }
        if (!unchosen.isEmpty()) {
            throw new InvalidUserDataException("Please choose which variant of "
                    + TextUtils.joinAsSentence(unchosen) + " you are handing over.");
        }
        if (!foreign.isEmpty()) {
            throw new InvalidUserDataException((foreign.size() == 1
                    ? "The variant you chose does not belong to " : "The variants you chose do not belong to ")
                    + TextUtils.joinAsSentence(foreign) + ".");
        }
        return issues;
    }

    // Paired the way the check screen pairs a link with a roster column: trimmed, and without regard to case.
    private Map<String, InventoryGoodieMapping> linksByName(Long eventId) {
        Map<String, InventoryGoodieMapping> links = new HashMap<>();
        mappingRepository.findByEventIdOrderByGoodieNameAsc(eventId)
                .forEach(link -> links.putIfAbsent(TextUtils.toMatchKey(link.getGoodieName()), link));
        return links;
    }

    private List<GoodieStockOption.Variant> variantsOf(Long itemId) {
        return variantLabeller.labelsForItem(itemId).entrySet().stream()
                .map(label -> new GoodieStockOption.Variant(label.getKey(), label.getValue()))
                .toList();
    }
}
