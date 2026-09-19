package com.timekeeper.bibexpo.inventory.service.util;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeOptionRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Spells an item's variants the way a person reads them &mdash; {@code 38}, or {@code 38 / Red}
 * &mdash; so an item's spellings and the values they mean can be shown side by side.
 *
 * <p>Every variant of the item is fetched in one pass rather than one query per variant, because
 * both callers want the whole set: one to label the spellings, the other to match a roster to them.
 */
@Component
@RequiredArgsConstructor
public class VariantLabeller {

    private static final String SEPARATOR = " / ";

    private final InventoryVariantRepository variantRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final InventoryAttributeOptionRepository optionRepository;

    /**
     * Every variant of one item, by id, spelled out. A variant carrying no values at all &mdash;
     * the single variant an item with no real variants gets &mdash; maps to an empty string, so it
     * is still present and still selectable.
     *
     * @param itemId the item whose variants to label
     * @return variant id to label, in the order the variants were created
     */
    @Transactional(readOnly = true)
    public Map<Long, String> labelsForItem(Long itemId) {
        List<InventoryVariant> variants = variantRepository.findByItemId(itemId);
        if (variants.isEmpty()) {
            return Map.of();
        }

        List<InventoryVariantAttributeValue> values = variantAttributeValueRepository
                .findByVariantIdIn(variants.stream().map(InventoryVariant::getId).toList());

        Map<Long, String> optionValues = optionRepository
                .findAllById(values.stream()
                        .map(InventoryVariantAttributeValue::getOptionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(InventoryAttributeOption::getId, InventoryAttributeOption::getValue));

        Map<Long, List<String>> spelledByVariant = values.stream().collect(Collectors.groupingBy(
                InventoryVariantAttributeValue::getVariantId,
                Collectors.mapping(value -> value.getOptionId() != null
                                ? optionValues.getOrDefault(value.getOptionId(), "")
                                : String.valueOf(value.getRawValue()),
                        Collectors.toList())));

        Map<Long, String> labels = new LinkedHashMap<>();
        for (InventoryVariant variant : variants) {
            List<String> spelled = spelledByVariant.getOrDefault(variant.getId(), List.of());
            labels.put(variant.getId(), String.join(SEPARATOR, spelled.stream().sorted().toList()));
        }
        return labels;
    }
}
