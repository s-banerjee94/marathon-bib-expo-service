package com.timekeeper.bibexpo.inventory.service.util;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAlias;
import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAliasRepository;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads what a participant's goody value means for one item, in an order that never guesses: the
 * item's own variant values, then the spellings it was taught, then its one variant when it varies by
 * nothing. The check screen and the counter both read through here, so a value cannot mean one
 * variant on the screen and another at the counter.
 */
@Component
@RequiredArgsConstructor
public class GoodieValueReader {

    private final VariantLabeller variantLabeller;
    private final InventoryVariantAliasRepository aliasRepository;

    /**
     * Loads one item's variants and spellings once, so any number of values can be read against them.
     *
     * @param itemId the item a goody is linked to
     * @return a reader for that item
     */
    public ItemReader forItem(Long itemId) {
        Map<Long, String> labels = variantLabeller.labelsForItem(itemId);
        Map<String, Long> ownValues = new HashMap<>();
        labels.forEach((variantId, label) -> {
            if (!label.isEmpty()) {
                ownValues.putIfAbsent(TextUtils.toMatchKey(label), variantId);
            }
        });
        Map<String, InventoryVariantAlias> spellings = new HashMap<>();
        aliasRepository.findByItemIdOrderBySourceValueAsc(itemId)
                .forEach(alias -> spellings.putIfAbsent(TextUtils.toMatchKey(alias.getSourceValue()), alias));
        return new ItemReader(labels, ownValues, spellings);
    }

    public record ItemReader(Map<Long, String> labels, Map<String, Long> ownValues,
                             Map<String, InventoryVariantAlias> spellings) {

        public Reading read(String value) {
            String key = TextUtils.toMatchKey(value);
            if (ownValues.containsKey(key)) {
                return new Reading(GoodieValueResolution.VARIANT, ownValues.get(key));
            }
            InventoryVariantAlias spelling = spellings.get(key);
            if (spelling != null) {
                return spelling.getVariantId() == null
                        ? new Reading(GoodieValueResolution.NOTHING_OWED, null)
                        : new Reading(GoodieValueResolution.ALIAS, spelling.getVariantId());
            }
            // An item that varies by nothing has one unnamed variant, and whoever is owed the goody is owed that one.
            if (ownValues.isEmpty() && labels.size() == 1) {
                return new Reading(GoodieValueResolution.SINGLE_VARIANT, labels.keySet().iterator().next());
            }
            return new Reading(GoodieValueResolution.UNRESOLVED, null);
        }
    }

    /**
     * @param resolution how the value was read
     * @param variantId  the variant it names; null when it names none
     */
    public record Reading(GoodieValueResolution resolution, Long variantId) {
    }
}
