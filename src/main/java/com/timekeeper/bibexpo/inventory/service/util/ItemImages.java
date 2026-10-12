package com.timekeeper.bibexpo.inventory.service.util;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantRepository;
import com.timekeeper.bibexpo.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The picture that stands for an item in a list: its own photo, else the first of its variants that has
 * one, else none.
 */
@Component
@RequiredArgsConstructor
public class ItemImages {

    private final InventoryVariantRepository variantRepository;
    private final StorageService storageService;

    /** A signed link per item id, in one variant query however many items; items without a picture are absent. */
    public Map<Long, String> urlsFor(List<InventoryItem> items) {
        List<Long> needVariants = items.stream()
                .filter(item -> item.getImageKey() == null).map(InventoryItem::getId).toList();
        Map<Long, List<InventoryVariant>> variants = needVariants.isEmpty() ? Map.of()
                : variantRepository.findByItemIdIn(needVariants).stream()
                        .collect(Collectors.groupingBy(InventoryVariant::getItemId));
        Map<Long, String> urls = new HashMap<>();
        for (InventoryItem item : items) {
            String key = keyFor(item, variants.getOrDefault(item.getId(), List.of()));
            if (key != null) {
                urls.put(item.getId(), storageService.createDownloadUrl(key));
            }
        }
        return urls;
    }

    /** A signed link for one item whose variants are already loaded, or null. */
    public String urlFor(InventoryItem item, List<InventoryVariant> variants) {
        return storageService.createDownloadUrl(keyFor(item, variants));
    }

    private static String keyFor(InventoryItem item, List<InventoryVariant> variants) {
        if (item.getImageKey() != null) {
            return item.getImageKey();
        }
        return variants.stream()
                .filter(v -> v.getImageKey() != null)
                .min(Comparator.comparing(InventoryVariant::getId))
                .map(InventoryVariant::getImageKey)
                .orElse(null);
    }
}
